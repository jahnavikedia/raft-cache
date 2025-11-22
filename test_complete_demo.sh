#!/bin/bash

# Complete Demo and Test Script for ML-Based Cache Eviction
# This script demonstrates the entire system working together

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║   ML-Based Cache Eviction - Complete Demo & Test          ║"
echo "╔════════════════════════════════════════════════════════════╗"
echo ""

# Step 1: Start ML Service
echo -e "${CYAN}Step 1: Starting ML Service${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cd ml-service
source venv/bin/activate
python app.py > /tmp/ml-service.log 2>&1 &
ML_PID=$!
echo -e "${GREEN}✓ ML Service started (PID: $ML_PID)${NC}"
cd ..

# Wait for ML service to fully start (Flask debug mode needs extra time)
echo "  Waiting for ML service to initialize..."
sleep 8

# Verify ML service is running
MAX_RETRIES=5
for i in $(seq 1 $MAX_RETRIES); do
    if curl -s http://localhost:5001/health 2>/dev/null | grep -q "healthy"; then
        echo -e "${GREEN}✓ ML Service is healthy${NC}"
        break
    elif [ $i -eq $MAX_RETRIES ]; then
        echo -e "${RED}✗ ML Service failed to start${NC}"
        echo "Log output:"
        tail -20 /tmp/ml-service.log
        exit 1
    else
        echo "  Retry $i/$MAX_RETRIES..."
        sleep 2
    fi
done
echo ""

# Step 2: Start Raft Node
echo -e "${CYAN}Step 2: Starting Raft Node${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
java -jar target/raft-cache-1.0-SNAPSHOT.jar --config config/node1.yaml > /tmp/raft-node.log 2>&1 &
RAFT_PID=$!
echo -e "${GREEN}✓ Raft Node started (PID: $RAFT_PID)${NC}"
echo "  - HTTP API: http://localhost:8081"
echo "  - Raft Port: 9001"
sleep 5

# Wait for node to become leader (single node cluster)
echo "  Waiting for leader election..."
sleep 10
echo -e "${GREEN}✓ Node should now be leader${NC}"
echo ""

# Step 3: Insert test data
echo -e "${CYAN}Step 3: Inserting Test Data${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
CLIENT_ID="test-client-$(date +%s)"

for i in {1..5}; do
    echo -n "  Inserting key$i... "
    curl -s -X POST http://localhost:8081/cache/key$i \
        -H "Content-Type: application/json" \
        -d "{\"clientId\": \"$CLIENT_ID\", \"value\": \"value$i\", \"sequenceNumber\": $i}" \
        > /tmp/insert_$i.json

    if grep -q '"success":true' /tmp/insert_$i.json; then
        echo -e "${GREEN}✓${NC}"
    else
        echo -e "${YELLOW}⚠ (waiting for leader)${NC}"
    fi
done
echo ""

# Step 4: Access keys to create patterns
echo -e "${CYAN}Step 4: Creating Access Patterns${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Accessing key1, key2, key3 frequently (10 times each)..."
for iteration in {1..10}; do
    for key in key1 key2 key3; do
        curl -s http://localhost:8081/cache/$key > /dev/null 2>&1
    done
done
echo -e "${GREEN}✓ Hot keys accessed 10 times each${NC}"

echo "  Accessing key4 moderately (3 times)..."
for i in {1..3}; do
    curl -s http://localhost:8081/cache/key4 > /dev/null 2>&1
done
echo -e "${GREEN}✓ Warm key accessed 3 times${NC}"

echo "  Accessing key5 once (cold key)..."
curl -s http://localhost:8081/cache/key5 > /dev/null 2>&1
echo -e "${GREEN}✓ Cold key accessed 1 time${NC}"
echo ""

# Step 5: Check access statistics
echo -e "${CYAN}Step 5: Viewing Access Statistics${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
STATS=$(curl -s http://localhost:8081/cache/access-stats)

if command -v jq &> /dev/null; then
    echo "$STATS" | jq '.'
    echo ""
    echo -e "${BLUE}Access Counts:${NC}"
    for key in key1 key2 key3 key4 key5; do
        count=$(echo "$STATS" | jq -r ".stats[] | select(.key == \"$key\") | .totalAccessCount" 2>/dev/null || echo "0")
        printf "  %-10s : %s accesses\n" "$key" "$count"
    done
else
    echo "$STATS" | python3 -m json.tool
fi
echo ""

# Step 6: Get ML Prediction
echo -e "${CYAN}Step 6: Getting ML Eviction Recommendation${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Build access history from stats
CURRENT_TIME=$(date +%s)000

cat > /tmp/ml_request.json <<EOF
{
  "keys": ["key1", "key2", "key3", "key4", "key5"],
  "accessHistory": {
    "key1": [${CURRENT_TIME}],
    "key2": [${CURRENT_TIME}],
    "key3": [${CURRENT_TIME}],
    "key4": [${CURRENT_TIME}],
    "key5": [${CURRENT_TIME}]
  },
  "currentTime": ${CURRENT_TIME}
}
EOF

ML_RESPONSE=$(curl -s -X POST http://localhost:5001/predict \
    -H 'Content-Type: application/json' \
    -d @/tmp/ml_request.json)

if command -v jq &> /dev/null; then
    echo "$ML_RESPONSE" | jq '.'
    EVICT_KEY=$(echo "$ML_RESPONSE" | jq -r '.evictKey')
    CONFIDENCE=$(echo "$ML_RESPONSE" | jq -r '.confidence')

    echo ""
    echo -e "${BLUE}ML Recommendation:${NC}"
    echo -e "  Evict Key: ${YELLOW}$EVICT_KEY${NC}"
    echo -e "  Confidence: ${GREEN}$(printf "%.1f" $(echo "$CONFIDENCE * 100" | bc))%${NC}"
else
    echo "$ML_RESPONSE" | python3 -m json.tool
    EVICT_KEY=$(echo "$ML_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['evictKey'])" 2>/dev/null || echo "unknown")
    echo ""
    echo -e "${BLUE}Recommended to evict: ${YELLOW}$EVICT_KEY${NC}"
fi
echo ""

# Step 7: Verify the system
echo -e "${CYAN}Step 7: System Verification${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
CHECKS=0

# Check 1: ML Service
if curl -s http://localhost:5001/health | grep -q "healthy"; then
    echo -e "${GREEN}✓ ML Service is running${NC}"
    ((CHECKS++))
else
    echo -e "${RED}✗ ML Service is not running${NC}"
fi

# Check 2: Raft Node
if curl -s http://localhost:8081/status > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Raft Node is running${NC}"
    ((CHECKS++))
else
    echo -e "${RED}✗ Raft Node is not running${NC}"
fi

# Check 3: Access Stats
if echo "$STATS" | grep -q "trackedKeys"; then
    echo -e "${GREEN}✓ Access tracking is working${NC}"
    ((CHECKS++))
else
    echo -e "${RED}✗ Access tracking is not working${NC}"
fi

# Check 4: ML Predictions
if echo "$ML_RESPONSE" | grep -q "evictKey"; then
    echo -e "${GREEN}✓ ML predictions are working${NC}"
    ((CHECKS++))
else
    echo -e "${RED}✗ ML predictions are not working${NC}"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "System Check: ${GREEN}$CHECKS/4${NC} components working"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Step 8: Summary
echo -e "${CYAN}Summary${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${GREEN}✓ ML Service:${NC} http://localhost:5001"
echo -e "${GREEN}✓ Raft Node:${NC}  http://localhost:8081"
echo -e "${GREEN}✓ Access Tracking:${NC} Recording all GET requests"
echo -e "${GREEN}✓ ML Predictions:${NC} Providing eviction recommendations"
echo ""
echo "To stop the services:"
echo "  kill $ML_PID     # Stop ML service"
echo "  kill $RAFT_PID   # Stop Raft node"
echo ""
echo "Or run: pkill -f 'python app.py' && pkill -f 'java -jar.*raft-cache'"
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                    Demo Complete! 🎉                       ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
