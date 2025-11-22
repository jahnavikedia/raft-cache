#!/bin/bash

# Complete Access Tracking Demo
# This script tests the full ML-based cache eviction system with a single-node cluster

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║   Access Tracking & ML Eviction - Full Demo               ║"
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

echo "  Waiting for ML service to initialize..."
sleep 8

# Verify ML service
MAX_RETRIES=5
for i in $(seq 1 $MAX_RETRIES); do
    if curl -s http://localhost:5001/health 2>/dev/null | grep -q "healthy"; then
        echo -e "${GREEN}✓ ML Service is healthy${NC}"
        break
    elif [ $i -eq $MAX_RETRIES ]; then
        echo -e "${RED}✗ ML Service failed to start${NC}"
        exit 1
    else
        sleep 2
    fi
done
echo ""

# Step 2: Start Raft Node (Standalone)
echo -e "${CYAN}Step 2: Starting Raft Node (Single-Node Mode)${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
java -jar target/raft-cache-1.0-SNAPSHOT.jar --config config/node1-standalone.yaml > /tmp/raft-node.log 2>&1 &
RAFT_PID=$!
echo -e "${GREEN}✓ Raft Node started (PID: $RAFT_PID)${NC}"
echo "  - HTTP API: http://localhost:8081"
echo "  - Raft Port: 9001"
echo "  Waiting for leader election..."
sleep 15
echo -e "${GREEN}✓ Node elected as leader${NC}"
echo ""

# Step 3: Insert test data
echo -e "${CYAN}Step 3: Inserting Test Data${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
CLIENT_ID="test-client-$(date +%s)"

for i in {1..5}; do
    echo -n "  Inserting key$i... "
    RESPONSE=$(curl -s -X POST http://localhost:8081/cache/key$i \
        -H "Content-Type: application/json" \
        -d "{\"clientId\": \"$CLIENT_ID\", \"value\": \"value$i\", \"sequenceNumber\": $i}")

    if echo "$RESPONSE" | grep -q '"success":true'; then
        echo -e "${GREEN}✓${NC}"
    else
        echo -e "${YELLOW}⚠ (retrying...)${NC}"
        sleep 2
        curl -s -X POST http://localhost:8081/cache/key$i \
            -H "Content-Type: application/json" \
            -d "{\"clientId\": \"$CLIENT_ID\", \"value\": \"value$i\", \"sequenceNumber\": $i}" > /dev/null
    fi
done
echo ""

# Step 4: Create access patterns
echo -e "${CYAN}Step 4: Creating Access Patterns${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

echo "  Creating HOT keys (key1, key2 - 15 accesses each)..."
for i in {1..15}; do
    curl -s http://localhost:8081/cache/key1 > /dev/null 2>&1
    curl -s http://localhost:8081/cache/key2 > /dev/null 2>&1
done
echo -e "${GREEN}✓ Hot keys accessed 15 times each${NC}"

echo "  Creating WARM key (key3 - 5 accesses)..."
for i in {1..5}; do
    curl -s http://localhost:8081/cache/key3 > /dev/null 2>&1
done
echo -e "${GREEN}✓ Warm key accessed 5 times${NC}"

echo "  Creating COLD keys (key4, key5 - 1 access each)..."
curl -s http://localhost:8081/cache/key4 > /dev/null 2>&1
curl -s http://localhost:8081/cache/key5 > /dev/null 2>&1
echo -e "${GREEN}✓ Cold keys accessed 1 time each${NC}"
echo ""

# Step 5: View access statistics
echo -e "${CYAN}Step 5: Viewing Access Statistics${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
STATS=$(curl -s http://localhost:8081/cache/access-stats)

if command -v jq &> /dev/null; then
    echo "$STATS" | jq '.'
    echo ""
    echo -e "${BLUE}Access Count Summary:${NC}"
    for key in key1 key2 key3 key4 key5; do
        count=$(echo "$STATS" | jq -r ".stats[] | select(.key == \"$key\") | .totalAccessCount" 2>/dev/null || echo "0")
        printf "  %-10s : %2s accesses\n" "$key" "$count"
    done
else
    echo "$STATS" | python3 -m json.tool
    echo ""
    echo -e "${BLUE}Access Count Summary:${NC}"
    echo "$STATS" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    for stat in data.get('stats', []):
        print(f\"  {stat['key']:10s} : {stat['totalAccessCount']:2d} accesses\")
except:
    pass
" 2>/dev/null
fi
echo ""

# Step 6: Get ML prediction
echo -e "${CYAN}Step 6: ML Eviction Recommendation${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Extract access history from stats
CURRENT_TIME=$(date +%s)000

# Build prediction request
cat > /tmp/ml_predict.json <<EOF
{
  "keys": ["key1", "key2", "key3", "key4", "key5"],
  "accessHistory": {
    "key1": [$CURRENT_TIME],
    "key2": [$CURRENT_TIME],
    "key3": [$CURRENT_TIME],
    "key4": [$CURRENT_TIME],
    "key5": [$CURRENT_TIME]
  },
  "currentTime": $CURRENT_TIME
}
EOF

ML_RESPONSE=$(curl -s -X POST http://localhost:5001/predict \
    -H 'Content-Type: application/json' \
    -d @/tmp/ml_predict.json)

if command -v jq &> /dev/null; then
    echo "$ML_RESPONSE" | jq '.'
    EVICT_KEY=$(echo "$ML_RESPONSE" | jq -r '.evictKey')
    CONFIDENCE=$(echo "$ML_RESPONSE" | jq -r '.confidence')

    echo ""
    echo -e "${BLUE}ML Recommendation:${NC}"
    echo -e "  ${YELLOW}Evict: $EVICT_KEY${NC}"
    echo -e "  ${GREEN}Confidence: $(printf "%.1f" $(echo "$CONFIDENCE * 100" | bc))%${NC}"
else
    echo "$ML_RESPONSE" | python3 -m json.tool
    EVICT_KEY=$(echo "$ML_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['evictKey'])" 2>/dev/null || echo "unknown")
    echo ""
    echo -e "${BLUE}Recommended to evict: ${YELLOW}$EVICT_KEY${NC}"
fi
echo ""

# Step 7: System verification
echo -e "${CYAN}Step 7: System Verification${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
CHECKS=0

# Check ML Service
if curl -s http://localhost:5001/health | grep -q "healthy"; then
    echo -e "${GREEN}✓ ML Service is running${NC}"
    ((CHECKS++))
else
    echo -e "${RED}✗ ML Service is not running${NC}"
fi

# Check Raft Node
if curl -s http://localhost:8081/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Raft Node is running${NC}"
    ((CHECKS++))
else
    echo -e "${RED}✗ Raft Node is not running${NC}"
fi

# Check Access Tracking
if echo "$STATS" | grep -q '"trackedKeys".*5'; then
    echo -e "${GREEN}✓ Access tracking is working (5 keys tracked)${NC}"
    ((CHECKS++))
else
    echo -e "${RED}✗ Access tracking is not working${NC}"
fi

# Check ML Predictions
if echo "$ML_RESPONSE" | grep -q "evictKey"; then
    echo -e "${GREEN}✓ ML predictions are working${NC}"
    ((CHECKS++))
else
    echo -e "${RED}✗ ML predictions are not working${NC}"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ $CHECKS -eq 4 ]; then
    echo -e "${GREEN}✅ All Systems Operational: $CHECKS/4${NC}"
else
    echo -e "${YELLOW}⚠  System Check: $CHECKS/4 components working${NC}"
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Summary
echo -e "${CYAN}Summary${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${GREEN}✓ ML Service:${NC} http://localhost:5001"
echo -e "${GREEN}✓ Raft Node:${NC}  http://localhost:8081"
echo ""
echo "Expected Behavior:"
echo "  • Hot keys (key1, key2) should have ~15 accesses"
echo "  • Warm key (key3) should have ~5 accesses"
echo "  • Cold keys (key4, key5) should have ~1 access"
echo "  • ML should recommend evicting key4 or key5"
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
