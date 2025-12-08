#!/bin/bash

# ==============================================================================
# SCRIPT: generate_junit_report.sh
# DESCRIPTION: Calculates performance bounds, parses JMeter log output for 
#              actual counts and failures, and generates a JUnit XML report.
# DEPENDENCIES: Standard Bash Shell, grep, awk, sed. No 'bc' is required.
# ==============================================================================

# Check if duration parameter is provided
if [ -z "$1" ]; then
    echo "Error: Test duration (in seconds) must be provided as the first argument."
    exit 1
fi

# --- Configuration & Input ---
DURATION_SECONDS=$1
LOG_FILE="/home/jenkins/logs/xxx.log"
OUTPUT_FILE="/home/jenkins/logs/xxx.xml"
EXPECTED_ALERTS_PER_HOUR=34000
TEST_SUITE_NAME="engineTest"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S")

# Scaling factor for mathematical rounding (using 100 for 2 decimal places)
# ROUNDING_FACTOR is 1/2 of the scaling factor (100)
SCALING_FACTOR=100
ROUNDING_FACTOR=50

# --- Calculations (Bash Integer Arithmetic with Rounding) ---

# 1. Calculate Expected Alerts (Scaled by 100)
# Formula: (H * D * 100) / 3600
# The division by 3600 converts DURATION_SECONDS to hours.
EXPECTED_ALERTS_SCALED=$(( EXPECTED_ALERTS_PER_HOUR * DURATION_SECONDS * SCALING_FACTOR / 3600 ))

# 2. Calculate Lower Bound (L = E * 0.95 = E_scaled * 95 / 100)
LOWER_BOUND_SCALED=$(( EXPECTED_ALERTS_SCALED * 95 / 100 ))

# 3. Calculate Upper Bound (U = E * 1.05 = E_scaled * 105 / 100)
UPPER_BOUND_SCALED=$(( EXPECTED_ALERTS_SCALED * 105 / 100 ))

# --- Convert Scaled values back to Integers (WITH ROUNDING) ---

# Add ROUNDING_FACTOR (50) before dividing by SCALING_FACTOR (100) to ensure rounding.
EXPECTED_ALERTS_INT=$(( (EXPECTED_ALERTS_SCALED + ROUNDING_FACTOR) / SCALING_FACTOR ))
LOWER_BOUND_INT=$(( (LOWER_BOUND_SCALED + ROUNDING_FACTOR) / SCALING_FACTOR ))
UPPER_BOUND_INT=$(( (UPPER_BOUND_SCALED + ROUNDING_FACTOR) / SCALING_FACTOR ))

echo "Duration (s): $DURATION_SECONDS | Expected Alerts: $EXPECTED_ALERTS_INT (Range: $LOWER_BOUND_INT - $UPPER_BOUND_INT)"

# --- Log Parsing ---

# 1. Real Alert Count
# Grep the line and use awk to print the 4th field (the number).
REAL_ALERT_COUNT=$(grep -oE 'Test has impacted [0-9]+ alerts for state Open' "$LOG_FILE" | awk '{print $4}' | head -n 1)
REAL_ALERT_COUNT=${REAL_ALERT_COUNT:-0} # Default to 0 if not found

# 2. Real Rule Execution Failures Count
# Grep the line and use awk to print the 5th field (the number).
REAL_RULE_EXEC_FAILURES_COUNT=$(grep -oE 'Rule execution failures count: [0-9]+' "$LOG_FILE" | awk '{print $5}' | head -n 1)
REAL_RULE_EXEC_FAILURES_COUNT=${REAL_RULE_EXEC_FAILURES_COUNT:-0} # Default to 0 if not found

# 3. Detailed Rule Execution Failures
# Grep the line and use sed to strip the prefix, leaving the detail string.
# 's/"/&quot;/g' ensures quotes inside the message are XML-escaped.
REAL_RULE_EXEC_FAILURES_DETAILS=$(grep -oE 'Detailed rule execution failures: .+' "$LOG_FILE" | sed -E 's/^Detailed rule execution failures: //; s/"/&quot;/g' | head -n 1)
REAL_RULE_EXEC_FAILURES_DETAILS=${REAL_RULE_EXEC_FAILURES_DETAILS:-"No details available."}

echo "Actual Alerts: $REAL_ALERT_COUNT | Failures: $REAL_RULE_EXEC_FAILURES_COUNT"

# --- Test Case Evaluation & XML Generation Setup ---

TESTS_RUN=0
FAILURES_COUNT=0
TEST_CASES_XML=""

# --- Test Case 1: Number of Alerts (Performance Test) ---
TESTS_RUN=$((TESTS_RUN + 1))
ALERT_TEST_NAME="Number of alerts created is close to expectedNumberOfALerts (+-5% tolerance)"

# Check if REAL_ALERT_COUNT is within the [LOWER_BOUND_INT, UPPER_BOUND_INT] range
if [ "$REAL_ALERT_COUNT" -ge "$LOWER_BOUND_INT" ] && [ "$REAL_ALERT_COUNT" -le "$UPPER_BOUND_INT" ]; then
    # PASS
    TEST_CASES_XML+='<testcase classname="'"${TEST_SUITE_NAME}"'" name="'"${ALERT_TEST_NAME}"'" time="0.0"></testcase>'
else
    # FAIL
    FAILURES_COUNT=$((FAILURES_COUNT + 1))
    FAILURE_MESSAGE="Real Alert Count ($REAL_ALERT_COUNT) should be between Lower Bound ($LOWER_BOUND_INT) and Upper Bound ($UPPER_BOUND_INT), but it's not."
    
    TEST_CASES_XML+='<testcase classname="'"${TEST_SUITE_NAME}"'" name="'"${ALERT_TEST_NAME}"'" time="0.0">'
    TEST_CASES_XML+='<failure type="AlertCountOutOfTolerance">'
    TEST_CASES_XML+="${FAILURE_MESSAGE}"
    TEST_CASES_XML+='</failure>'
    TEST_CASES_XML+='</testcase>'
fi

# --- Test Case 2: Rule Execution Failures (Functional Test) ---
TESTS_RUN=$((TESTS_RUN + 1))
FAILURE_TEST_NAME="Number of rule execution failures is 0"

# Check if REAL_RULE_EXEC_FAILURES_COUNT is 0
if [ "$REAL_RULE_EXEC_FAILURES_COUNT" -eq 0 ]; then
    # PASS
    TEST_CASES_XML+='<testcase classname="'"${TEST_SUITE_NAME}"'" name="'"${FAILURE_TEST_NAME}"'" time="0.0"></testcase>'
else
    # FAIL
    FAILURES_COUNT=$((FAILURES_COUNT + 1))
    FAILURE_MESSAGE="There are ${REAL_RULE_EXEC_FAILURES_COUNT} failures, details: ${REAL_RULE_EXEC_FAILURES_DETAILS}"
    
    TEST_CASES_XML+='<testcase classname="'"${TEST_SUITE_NAME}"'" name="'"${FAILURE_TEST_NAME}"'" time="0.0">'
    TEST_CASES_XML+='<failure type="RuleFailuresFound">'
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
echo "✅ Report Summary: $TESTS_RUN tests run with $FAILURES_COUNT failures."
echo "File saved to: $OUTPUT_FILE"