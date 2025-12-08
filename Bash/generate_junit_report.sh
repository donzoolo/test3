#!/bin/bash

# Check if duration parameter is provided
if [ -z "$1" ]; then
    echo "Error: Test duration (in seconds) must be provided as the first argument."
    exit 1
fi

# --- Configuration ---
DURATION_SECONDS=$1
LOG_FILE="/home/jenkins/logs/xxx.log"
OUTPUT_FILE="/home/jenkins/logs/xxx.xml"
EXPECTED_ALERTS_PER_HOUR=34000
TEST_SUITE_NAME="engineTest"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S")

# --- Calculations (Note: Shell arithmetic uses integers, using 'bc' for floating point) ---

# Calculate the duration fraction in hours: duration / 3600
DURATION_HOURS=$(echo "scale=8; $DURATION_SECONDS / 3600" | bc)

# Calculate expected alerts: E = H * (duration/3600)
EXPECTED_ALERTS=$(echo "scale=0; $EXPECTED_ALERTS_PER_HOUR * $DURATION_HOURS / 1" | bc)
EXPECTED_ALERTS_INT=${EXPECTED_ALERTS%.*}

# Calculate lower bound: L = E * 0.95
LOWER_BOUND=$(echo "scale=0; $EXPECTED_ALERTS * 0.95 / 1" | bc)
LOWER_BOUND_INT=${LOWER_BOUND%.*}

# Calculate upper bound: U = E * 1.05
UPPER_BOUND=$(echo "scale=0; $EXPECTED_ALERTS * 1.05 / 1" | bc)
UPPER_BOUND_INT=${UPPER_BOUND%.*}

echo "Test Duration (s): $DURATION_SECONDS"
echo "Expected Alerts: $EXPECTED_ALERTS_INT (Range: $LOWER_BOUND_INT - $UPPER_BOUND_INT)"

# --- Log Parsing ---

# 1. Real Alert Count
# Grep the line and then use awk to print the 4th field (the number)
REAL_ALERT_COUNT=$(grep -oE 'Test has impacted [0-9]+ alerts for state Open' "$LOG_FILE" | awk '{print $4}' | head -n 1)
REAL_ALERT_COUNT=${REAL_ALERT_COUNT:-0} # Default to 0 if not found

# 2. Real Rule Execution Failures Count
# Grep the line and then use awk to print the 5th field (the number)
REAL_RULE_EXEC_FAILURES_COUNT=$(grep -oE 'Rule execution failures count: [0-9]+' "$LOG_FILE" | awk '{print $5}' | head -n 1)
REAL_RULE_EXEC_FAILURES_COUNT=${REAL_RULE_EXEC_FAILURES_COUNT:-0} # Default to 0 if not found

# 3. Detailed Rule Execution Failures
# Grep the line and use sed to remove everything up to and including the first colon and space.
REAL_RULE_EXEC_FAILURES_DETAILS=$(grep -oE 'Detailed rule execution failures: .+' "$LOG_FILE" | sed -E 's/^Detailed rule execution failures: //; s/"/&quot;/g' | head -n 1)
REAL_RULE_EXEC_FAILURES_DETAILS=${REAL_RULE_EXEC_FAILURES_DETAILS:-"No details available."}

echo "Real Alert Count: $REAL_ALERT_COUNT"
echo "Real Failures Count: $REAL_RULE_EXEC_FAILURES_COUNT"
echo "Failure Details: $REAL_RULE_EXEC_FAILURES_DETAILS"

# --- Test Case Evaluation ---

TESTS_RUN=0
FAILURES_COUNT=0
TEST_CASES_XML=""

# --- Test Case 1: Number of Alerts ---
TESTS_RUN=$((TESTS_RUN + 1))
ALERT_TEST_NAME="Number of alerts created is close to expectedNumberOfALerts (+-5% tolerance)"

if [ "$REAL_ALERT_COUNT" -ge "$LOWER_BOUND_INT" ] && [ "$REAL_ALERT_COUNT" -le "$UPPER_BOUND_INT" ]; then
    # PASS
    TEST_CASES_XML+='<testcase classname="'"${TEST_SUITE_NAME}"'" name="'"${ALERT_TEST_NAME}"'" time="0.0"></testcase>'
else
    # FAIL
    FAILURES_COUNT=$((FAILURES_COUNT + 1))
    FAILURE_MESSAGE="Real Alert Count ($REAL_ALERT_COUNT) should be between Lower Bound ($LOWER_BOUND_INT) and Upper Bound ($UPPER_BOUND_INT), but it's not."
    
    TEST_CASES_XML+='<testcase classname="'"${TEST_SUITE_NAME}"'" name="'"${ALERT_TEST_NAME}"'" time="0.0">'
    TEST_CASES_XML+='<failure type="ThresholdExceeded">'
    TEST_CASES_XML+="${FAILURE_MESSAGE}"
    TEST_CASES_XML+='</failure>'
    TEST_CASES_XML+='</testcase>'
fi

# --- Test Case 2: Rule Execution Failures ---
TESTS_RUN=$((TESTS_RUN + 1))
FAILURE_TEST_NAME="Number of rule execution failures is 0"

if [ "$REAL_RULE_EXEC_FAILURES_COUNT" -eq 0 ]; then
    # PASS
    TEST_CASES_XML+='<testcase classname="'"${TEST_SUITE_NAME}"'" name="'"${FAILURE_TEST_NAME}"'" time="0.0"></testcase>'
else
    # FAIL
    FAILURES_COUNT=$((FAILURES_COUNT + 1))
    FAILURE_MESSAGE="There are ${REAL_RULE_EXEC_FAILURES_COUNT} failures. Details: ${REAL_RULE_EXEC_FAILURES_DETAILS}"
    
    TEST_CASES_XML+='<testcase classname="'"${TEST_SUITE_NAME}"'" name="'"${FAILURE_TEST_NAME}"'" time="0.0">'
    TEST_CASES_XML+='<failure type="RuleFailuresPresent">'
    TEST_CASES_XML+="${FAILURE_MESSAGE}"
    TEST_CASES_XML+='</failure>'
    TEST_CASES_XML+='</testcase>'
fi

# --- Construct and Save Final XML ---

{
echo '<?xml version="1.0" encoding="UTF-8"?>'
# Final Test Suite line with dynamic totals
echo "<testsuite name=\"${TEST_SUITE_NAME}\" tests=\"${TESTS_RUN}\" failures=\"${FAILURES_COUNT}\" errors=\"0\" time=\"${DURATION_SECONDS}\" timestamp=\"${TIMESTAMP}\">"

# Insert the generated test cases
echo "${TEST_CASES_XML}"

echo '</testsuite>'

} > "${OUTPUT_FILE}"

echo "---"
echo "✅ Successfully generated JUnit XML report with $TESTS_RUN tests and $FAILURES_COUNT failures."
echo "File saved to: $OUTPUT_FILE"