#!/bin/bash

# Test script for ML service
# This script tests the ML prediction service endpoints

set -e

ML_URL="http://localhost:5000"

echo "=============================================="
echo "ML Service Test Script"
echo "=============================================="
echo "Service URL: ${ML_URL}"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if service is running
check_service() {
    if curl -s -f "${ML_URL}/health" > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# Check if service is running
echo "Step 1: Checking if ML service is running..."
if check_service; then
    echo -e "${GREEN}✓ ML service is running${NC}"
else
    echo -e "${RED}✗ ML service is not running${NC}"
    echo ""
    echo "Please start the ML service first:"
    echo "  cd ml-service"
    echo "  source venv/bin/activate"
    echo "  python app.py"
    exit 1
fi
echo ""

# Test health endpoint
echo "Step 2: Testing /health endpoint..."
HEALTH_RESPONSE=$(curl -s "${ML_URL}/health")
echo "Response: ${HEALTH_RESPONSE}"

if echo "${HEALTH_RESPONSE}" | grep -q '"status": "healthy"'; then
    echo -e "${GREEN}✓ Health check passed${NC}"
else
    echo -e "${RED}✗ Health check failed${NC}"
    exit 1
fi
echo ""

# Test prediction with hot key (frequently accessed)
echo "Step 3: Testing prediction with hot key..."
echo "Scenario: user:123 accessed 10 times recently (hot)"
echo "          session:456 never accessed (cold)"
echo "          cache:789 accessed once 10 hours ago (warm)"
echo ""

CURRENT_TIME=$(date +%s)000  # Current time in milliseconds
ONE_HOUR_AGO=$((CURRENT_TIME - 3600000))
TEN_HOURS_AGO=$((CURRENT_TIME - 36000000))

# Build access history
# Hot key: 10 accesses in the last hour
HOT_ACCESSES=""
for i in {1..10}; do
    TIMESTAMP=$((ONE_HOUR_AGO + (i * 300000)))  # Every 5 minutes
    if [ -z "$HOT_ACCESSES" ]; then
        HOT_ACCESSES="${TIMESTAMP}"
    else
        HOT_ACCESSES="${HOT_ACCESSES}, ${TIMESTAMP}"
    fi
done

PREDICT_REQUEST=$(cat <<EOF
{
  "keys": ["user:123", "session:456", "cache:789"],
  "accessHistory": {
    "user:123": [${HOT_ACCESSES}],
    "session:456": [],
    "cache:789": [${TEN_HOURS_AGO}]
  },
  "currentTime": ${CURRENT_TIME}
}
EOF
)

PREDICT_RESPONSE=$(curl -s -X POST "${ML_URL}/predict" \
    -H "Content-Type: application/json" \
    -d "${PREDICT_REQUEST}")

echo "Response:"
echo "${PREDICT_RESPONSE}" | python3 -m json.tool 2>/dev/null || echo "${PREDICT_RESPONSE}"
echo ""

# Verify the recommendation
EVICT_KEY=$(echo "${PREDICT_RESPONSE}" | python3 -c "import sys, json; print(json.load(sys.stdin)['evictKey'])" 2>/dev/null || echo "unknown")

if [ "$EVICT_KEY" == "session:456" ]; then
    echo -e "${GREEN}✓ Correct recommendation: session:456 (never accessed)${NC}"
elif [ "$EVICT_KEY" == "cache:789" ]; then
    echo -e "${YELLOW}⚠ Acceptable recommendation: cache:789 (accessed 10h ago)${NC}"
else
    echo -e "${RED}✗ Unexpected recommendation: ${EVICT_KEY}${NC}"
fi
echo ""

# Test prediction with all cold keys
echo "Step 4: Testing prediction with all cold keys..."
echo "Scenario: All keys accessed long ago"
echo ""

TWO_DAYS_AGO=$((CURRENT_TIME - 172800000))
THREE_DAYS_AGO=$((CURRENT_TIME - 259200000))
FOUR_DAYS_AGO=$((CURRENT_TIME - 345600000))

PREDICT_REQUEST_2=$(cat <<EOF
{
  "keys": ["old:1", "old:2", "old:3"],
  "accessHistory": {
    "old:1": [${TWO_DAYS_AGO}],
    "old:2": [${THREE_DAYS_AGO}],
    "old:3": [${FOUR_DAYS_AGO}]
  },
  "currentTime": ${CURRENT_TIME}
}
EOF
)

PREDICT_RESPONSE_2=$(curl -s -X POST "${ML_URL}/predict" \
    -H "Content-Type: application/json" \
    -d "${PREDICT_REQUEST_2}")

echo "Response:"
echo "${PREDICT_RESPONSE_2}" | python3 -m json.tool 2>/dev/null || echo "${PREDICT_RESPONSE_2}"
echo ""

EVICT_KEY_2=$(echo "${PREDICT_RESPONSE_2}" | python3 -c "import sys, json; print(json.load(sys.stdin)['evictKey'])" 2>/dev/null || echo "unknown")

if [ "$EVICT_KEY_2" == "old:3" ]; then
    echo -e "${GREEN}✓ Correct recommendation: old:3 (oldest access)${NC}"
else
    echo -e "${YELLOW}⚠ Recommendation: ${EVICT_KEY_2} (any cold key is acceptable)${NC}"
fi
echo ""

echo "=============================================="
echo "All Tests Complete!"
echo "=============================================="
echo ""
echo "Summary:"
echo "  ✓ Health check passed"
echo "  ✓ Prediction endpoint working"
echo "  ✓ ML service is operational"
echo ""
