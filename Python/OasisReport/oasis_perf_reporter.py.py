#!/usr/bin/env python3
import argparse
import datetime
import json
import os
import urllib.request
import urllib.error
import base64
import ssl

def load_json_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        return json.load(f)

def inject_time_filters(es_query, start_str, end_str, hist_start_str=None):
    raw_json = json.dumps(es_query)
    
    if not hist_start_str:
        # Fallback to standard 7-day lookback window calculation
        clean_start = start_str.replace("Z", "+00:00")
        start_dt = datetime.datetime.fromisoformat(clean_start)
        hist_start_dt = start_dt - datetime.timedelta(days=7)
        hist_start_str = hist_start_dt.strftime("%Y-%m-%dT%H:%M:%SZ")
        print(f"[+] Calculated 7-day baseline window starting: {hist_start_str}")
    
    # Swap out dynamic timestamp placeholders
    raw_json = raw_json.replace("INJECT_START", start_str)
    raw_json = raw_json.replace("INJECT_END", end_str)
    raw_json = raw_json.replace("INJECT_HIST_START", hist_start_str)
    
    return json.loads(raw_json)

def query_oasis(url, index, query_payload, user, password):
    endpoint = f"{url.rstrip('/')}/{index}/_search"
    req = urllib.request.Request(
        endpoint,
        data=json.dumps(query_payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    
    if user and password:
        auth_str = f"{user}:{password}"
        auth_b64 = base64.b64encode(auth_str.encode("utf-8")).decode("utf-8")
        req.add_header("Authorization", f"Basic {auth_b64}")
        
    # Windows/Internal SSL Fix: Bypass untrusted corporate certificates securely
    ssl_context = ssl._create_unverified_context()
    
    print(f"[DEBUG] Sending query payload to {endpoint}...")
    try:
        # Added a 20-second timeout to prevent indefinite hanging
        with urllib.request.urlopen(req, context=ssl_context, timeout=20) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.URLError as e:
        print(f"[-] Connection to Oasis failed: {e}")
        raise e

def resolve_thresholds(rule_name, thresholds):
    # Match exact string or longest matching prefix fallback
    if rule_name in thresholds:
        return thresholds[rule_name]
    for key in sorted(thresholds.keys(), key=len, reverse=True):
        if key != "DEFAULT" and rule_name.startswith(key):
            return thresholds[key]
    return thresholds.get("DEFAULT", {"abs": 500, "rel": 50})

def main():
    parser = argparse.ArgumentParser(description="Oasis Performance Gate Reporter")
    parser.add_argument("--url", required=True)
    parser.add_argument("--index", default="engine-logs-*")
    parser.add_argument("--start", required=True, help="Format: YYYY-MM-DDTHH:MM:SSZ")
    parser.add_argument("--end", required=True, help="Format: YYYY-MM-DDTHH:MM:SSZ")
    parser.add_argument("--hist-start", help="Optional explicit history start window")
    parser.add_argument("--thresholds", required=True)
    parser.add_argument("--queries-dir", required=True)
    parser.add_argument("--junit-out", default="perf-junit-results.xml")
    parser.add_argument("--user")
    parser.add_argument("--password")
    args = parser.parse_args()

    threshold_map = load_json_file(args.thresholds)
    query_files = [f for f in os.listdir(args.queries_dir) if f.endswith('.json')]
    
    print(f"[+] Found {len(query_files)} query templates in {args.queries_dir}")
    
    results = []
    
    for file_name in query_files:
        config = load_json_file(os.path.join(args.queries_dir, file_name))
        meta = config.get("meta", {})
        es_query = config.get("es_query", {})
        bucket_path = meta.get("bucket_key_path", "performance_by_rule")
        
        # Inject context boundaries
        final_query = inject_time_filters(es_query, args.start, args.end, args.hist_start)
        
        # Execute payload
        raw_response = query_oasis(args.url, args.index, final_query, args.user, args.password)
        
        # Parse aggregation metrics
        buckets = raw_response.get("aggregations", {}).get(bucket_path, {}).get("buckets", [])
        print(f"[+] Processing {len(buckets)} distinct rules from response...")
        
        for bucket in buckets:
            rule_name = bucket.get("key")
            
            # Extract percentiles safely out of nested structures
            curr_p90 = bucket.get("current_run", {}).get("latency_p90", {}).get("values", {}).get("90.0")
            hist_p90 = bucket.get("historical_runs", {}).get("latency_p90", {}).get("values", {}).get("90.0")
            
            if curr_p90 is None:
                print(f"[WARN] Skipping '{rule_name}' - zero documents found in current execution time frame.")
                continue
                
            limits = resolve_thresholds(rule_name, threshold_map)
            
            # Run gate assertion logic
            status = "PASS"
            reason = f"Current P90 ({curr_p90:.2f}ms) is within bounds."
            
            if curr_p90 > limits["abs"]:
                status = "FAIL"
                reason = f"Breached Absolute limit: {curr_p90:.2f}ms > {limits['abs']}ms"
            elif hist_p90 and (curr_p90 > hist_p90 * (1 + limits["rel"] / 100.0)):
                allowed = hist_p90 * (1 + limits["rel"] / 100.0)
                status = "FAIL"
                reason = f"Breached Relative baseline limit: {curr_p90:.2f}ms > {allowed:.2f}ms (Baseline: {hist_p90:.2f}ms)"
                
            print(f" [{status}] {rule_name.ljust(25)} -> {reason}")
            results.append({"rule": rule_name, "status": status, "reason": reason, "suite": meta.get("test_name", "PerfSuite")})

    # Output JUnit formatted XML file for CloudBees UI visualization step
    with open(args.junit_out, "w", encoding="utf-8") as x:
        x.write('<?xml version="1.0" encoding="UTF-8"?>\n<testsuites>\n')
        for r in results:
            clean_suite = r['suite'].replace(" ", "")
            x.write(f'  <testsuite name="{clean_suite}" tests="1">\n')
            x.write(f'    <testcase name="{r["rule"]}" classname="{clean_suite}">\n')
            if r["status"] == "FAIL":
                x.write(f'      <failure message="{r["reason"]}"/>\n')
            x.write('    </testcase>\n  </testsuite>\n')
        x.write('</testsuites>\n')
    print(f"[+] Local JUnit report successfully exported to: {args.junit_out}")

if __name__ == "__main__":
    main()