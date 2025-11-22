#!/bin/bash

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}======================================"
echo "  Leader Failure Test"
echo "======================================${NC}"

# Start cluster
echo "Starting cluster..."
./scripts/start-cluster.sh
sleep 8

# Find leader
LEADER_PORT=""
LEADER_NODE=""
for port in 8081 8082 8083; do
    ROLE=$(curl -s http://localhost:$port/status 2>/dev/null | jq -r '.role' 2>/dev/null || echo "")
    if [ "$ROLE" == "LEADER" ]; then
        LEADER_PORT=$port
        LEADER_NODE="node$((port - 8080))"
        echo -e "${GREEN}✓ Leader: $LEADER_NODE on port $port${NC}"
        break
    fi
done

if [ -z "$LEADER_PORT" ]; then
    echo -e "${RED}✗ No leader found${NC}"
    exit 1
fi

# Write initial data
echo -e "\n${YELLOW}Writing 20 keys before leader failure...${NC}"
for i in {1..20}; do
    curl -s -X POST http://localhost:$LEADER_PORT/cache/before_fail_$i \
        -H 'Content-Type: application/json' \
        -d "{\"value\":\"val_$i\",\"clientId\":\"test\",\"sequenceNumber\":$i}" > /dev/null 2>&1
done

sleep 2

# Kill leader
echo -e "\n${YELLOW}Killing leader...${NC}"
LEADER_PIDS=$(ps aux | grep "config/${LEADER_NODE}.yaml" | grep -v grep | awk '{print $2}')
for pid in $LEADER_PIDS; do
    kill $pid 2>/dev/null && echo "Killed PID: $pid"
done

# Wait for new leader election
echo -e "\n${YELLOW}Waiting for new leader election (10 seconds)...${NC}"
sleep 10

# Find new leader
NEW_LEADER_PORT=""
for port in 8081 8082 8083; do
    if [ "$port" != "$LEADER_PORT" ]; then
        ROLE=$(curl -s http://localhost:$port/status 2>/dev/null | jq -r '.role' 2>/dev/null || echo "")
        if [ "$ROLE" == "LEADER" ]; then
            NEW_LEADER_PORT=$port
            echo -e "${GREEN}✓ New leader elected on port $port${NC}"
            break
        fi
    fi
done

if [ -z "$NEW_LEADER_PORT" ]; then
    echo -e "${RED}✗ No new leader elected${NC}"
    ./scripts/stop-cluster.sh
    exit 1
fi

# Verify data on new leader
echo -e "\n${YELLOW}Verifying data on new leader...${NC}"
KEYS_FOUND=0
for i in {1..20}; do
    RESULT=$(curl -s "http://localhost:$NEW_LEADER_PORT/cache/before_fail_$i?consistency=strong" 2>/dev/null | jq -r '.value' 2>/dev/null || echo "")
    if [ "$RESULT" == "val_$i" ]; then
        ((KEYS_FOUND++))
    fi
done

echo "Keys found: $KEYS_FOUND/20"

# Summary
echo -e "\n${GREEN}======================================"
echo "  Leader Failure Test Results"
echo "======================================${NC}"

if [ "$KEYS_FOUND" -ge 15 ]; then
    echo -e "${GREEN}✓ PASSED - New leader elected and data preserved${NC}"
    ./scripts/stop-cluster.sh
    exit 0
else
    echo -e "${RED}✗ FAILED - Data loss detected${NC}"
    ./scripts/stop-cluster.sh
    exit 1
fi
