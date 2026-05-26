#!/usr/bin/env python3
import os
import json
import sys
import argparse
import urllib.request
import datetime
import xml.etree.ElementTree as ET
from xml.dom import minidom
import base64

def load_json_file(file_path):
    try:
        with open(file_path, 'r') as f: return json.load(f)
    except Exception as e:
        print(f"[-] Failed loading config file {file_path}: {e}"); sys.exit(1)

def inject_time_filters(es_query, start_str, end_str):
    """
    Parses start time, evaluates a 7-day look-back delta window,
    and maps them to string keys inside the raw JSON query document.
    """
    raw_json = json.dumps(es_query)
    
    # Calculate historical 7 day lookback date window
    # ISO formats can contain Z or offsets; trimming strings safely for basic parsing
    clean_start = start_str.replace("Z", "+00:00")
    start_dt = datetime.datetime.fromisoformat(clean_start)
    hist_start_dt = start_dt - datetime.timedelta(days=7)
    hist_start_str = hist_start_dt.strftime("%Y-%m-%dT%H:%M:%SZ")
    
    # Map mock variables inside the json query template target
    raw_json = raw_json.replace("INJECT_START", start_str)
    raw_json = raw_json.replace("INJECT_END", end_str)
    raw_json = raw_json.replace("INJECT_HIST_START", hist_start_str)
    
    return json.loads(raw_json)

def query_oasis(url, index, payload, user=None, pwd=None):
    target_url = f"{url.rstrip('/')}/{index}/_search"
    headers = {"Content-Type": "application/json"}
    if user and pwd:
        auth = base64.b64encode(f"{user}:{pwd}".encode()).decode()
        headers["Authorization"] = f"Basic {auth}"
    
    req = urllib.request.Request(target_url, data=json.dumps(payload).encode(), headers=headers, method='POST')
    try:
        with urllib.request.urlopen(req) as res: return json.loads(res.read().decode())
    except Exception as e:
        print(f"[-] Query Failed to {target_url}: {e}"); return None

def process_metrics(test_name, bucket_path, es_response, thresholds):
    buckets = es_response.get("aggregations", {}).get(bucket_path, {}).get("buckets", [])
    records = []
    
    for bucket in buckets:
        rule_id = bucket["key"]
        limits = thresholds.get(rule_id)
        if not limits:
            continue # If rule isn't tracked in Jenkins meteredValues configuration list, skip it.

        # Fetch p90 metrics out of nested run filters
        current_p90 = bucket.get("current_run", {}).get("latency_p90", {}).get("values", {}).get("90.0", 0.0) or 0.0
        historical_p90 = bucket.get("historical_runs", {}).get("historical_p90", {}).get("values", {}).get("90.0", 0.0) or 0.0
        doc_count = bucket.get("current_run", {}).get("doc_count", 0)

        # 1. Absolute Check Evaluation
        if "abs" in limits:
            abs_limit = float(limits["abs"])
            records.append({
                "suite": test_name, "rule_id": rule_id, "type": "Absolute",
                "actual": current_p90, "limit": abs_limit, "doc_count": doc_count,
                "failed": current_p90 > abs_limit, "msg": f"Current Run p90 ({current_p90:.1f}ms) exceeded SLA threshold ({abs_limit:.1f}ms)"
            })
            
        # 2. Relative Check Evaluation (% variance against last 7 days average performance baseline)
        if "rel" in limits and historical_p90 > 0:
            rel_percent = float(limits["rel"])
            max_allowed_rel = historical_p90 * (1.0 + (rel_percent / 100.0))
            records.append({
                "suite": test_name, "rule_id": rule_id, "type": "Relative",
                "actual": current_p90, "limit": max_allowed_rel, "doc_count": doc_count,
                "failed": current_p90 > max_allowed_rel,
                "msg": f"Current p90 ({current_p90:.1f}ms) degraded > {rel_percent}% over history baseline ({historical_p90:.1f}ms). Max target was {max_allowed_rel:.1f}ms"
            })
            
    return records

def write_junit_report(results, output_path):
    test_suite = ET.Element("testsuite", {
        "name": "oasis.jmeter.mimic.suite", "tests": str(len(results)), "failures": str(sum(1 for r in results if r["failed"]))
    })
    for r in results:
        test_case = ET.SubElement(test_suite, "testcase", {
            "classname": f"PerfTest.{r['rule_id']}", "name": f"verify_{r['type'].lower()}_p90", "time": "0.0"
        })
        if r["failed"]:
            fail_el = ET.SubElement(test_case, "failure", {"message": r["msg"], "type": "PerfSLAException"})
            fail_el.text = f"Rule: {r['rule_id']}\nAssertion Type: {r['type']}\nDetails: {r['msg']}\nInvocations This Run: {r['doc_count']}"
            
    xml_str = minidom.parseString(ET.tostring(test_suite, 'utf-8')).toprettyxml(indent="  ")
    with open(output_path, "w", encoding="utf-8") as f: f.write(xml_str)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--index", default="engine-logs-*")
    parser.add_argument("--start", required=True)
    parser.add_argument("--end", required=True)
    parser.add_argument("--thresholds", required=True)
    parser.add_argument("--queries-dir", required=True)
    parser.add_argument("--junit-out", default="perf-junit-results.xml")
    parser.add_argument("--user")
    parser.add_argument("--password")
    args = parser.parse_args()

    thresholds = load_json_file(args.thresholds)
    all_records = []
    global_success = True

    query_files = [f for f in os.listdir(args.queries_dir) if f.endswith('.json')]
    for file_name in query_files:
        config = load_json_file(os.path.join(args.queries_dir, file_name))
        meta = config.get("meta", {})
        
        # Inject dynamic pipeline run windows (Both Active and Historical)
        final_query = inject_time_filters(config.get("es_query", {}), args.start, args.end)
        response = query_oasis(args.url, args.index, final_query, args.user, args.password)
        
        if response:
            all_records.extend(process_metrics(meta.get("test_name"), meta.get("bucket_key_path"), response, thresholds))

    # Output Scannable Matrix
    print("\n" + "="*110)
    print(f"{'RULE TARGET':<25} | {'CHECK TYPE':<10} | {'ACTUAL p90':<12} | {'SLA LIMIT':<12} | STATUS | FAILURE DETAILS")
    print("="*110)
    for r in all_records:
        status = "[FAIL]" if r["failed"] else "[PASS]"
        if r["failed"]: global_success = False
        detail = r["msg"] if r["failed"] else "Within acceptable performance tolerances."
        print(f"{r['rule_id'][:23]:<25} | {r['type']:<10} | {r['actual']:<12.1f} | {r['limit']:<12.1f} | {status:<6} | {detail}")
    print("="*110)

    if all_records:
        write_junit_report(all_records, args.junit_out)
    sys.exit(0 if global_success else 1)

if __name__ == "__main__":
    main()