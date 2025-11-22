#!/bin/bash

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}=========================================="
echo "  Follower Recovery Test (Simplified)"
echo "==========================================${NC}"

# Start cluster
echo "Starting cluster..."
./scripts/start-cluster.sh
sleep 8

# Find leader and a follower
LEADER_PORT=""
FOLLOWER_PORT=""

for port in 8081 8082 8083; do
    ROLE=$(curl -s http://localhost:$port/status 2>/dev/null | jq -r '.role' 2>/dev/null || echo "")
    if [ "$ROLE" == "LEADER" ]; then
        LEADER_PORT=$port
    elif [ -z "$FOLLOWER_PORT" ]; then
        FOLLOWER_PORT=$port
    fi
done

if [ -z "$LEADER_PORT" ] || [ -z "$FOLLOWER_PORT" ]; then
    echo -e "${RED}✗ Could not find leader and follower${NC}"
    ./scripts/stop-cluster.sh
    exit 1
fi

echo -e "${GREEN}✓ Leader on port $LEADER_PORT${NC}"
echo -e "${GREEN}✓ Follower on port $FOLLOWER_PORT${NC}"

# Write 30 keys
echo -e "\n${YELLOW}Writing 30 keys to leader...${NC}"
for i in {1..30}; do
    curl -s -X POST http://localhost:$LEADER_PORT/cache/recovery_test_$i \
        -H 'Content-Type: application/json' \
        -d "{\"value\":\"val_$i\",\"clientId\":\"test\",\"sequenceNumber\":$i}" > /dev/null 2>&1
done

# Wait for replication
echo "Waiting 3 seconds for replication..."
sleep 3

# Verify follower has the data BEFORE crash
echo -e "\n${YELLOW}Verifying follower has data before crash...${NC}"
BEFORE_KEYS=0
for i in {1..10}; do
    RESULT=$(curl -s "http://localhost:$FOLLOWER_PORT/cache/recovery_test_$i?consistency=eventual" 2>/dev/null | jq -r '.value' 2>/dev/null || echo "")
    if [ "$RESULT" == "val_$i" ]; then
        ((BEFORE_KEYS++))
    fi
done
echo "Follower has $BEFORE_KEYS/10 keys before crash"

# Kill follower
echo -e "\n${YELLOW}Killing follower...${NC}"
FOLLOWER_PIDS=$(ps aux | grep ":$FOLLOWER_PORT" | grep java | grep -v grep | awk '{print $2}')
for pid in $FOLLOWER_PIDS; do
    kill $pid 2>/dev/null && echo "Killed PID: $pid"
done

sleep 2

# Write more data while follower is down
echo -e "\n${YELLOW}Writing 20 more keys while follower is down...${NC}"
for i in {31..50}; do
    curl -s -X POST http://localhost:$LEADER_PORT/cache/recovery_test_$i \
        -H 'Content-Type: application/json' \
        -d "{\"value\":\"val_$i\",\"clientId\":\"test\",\"sequenceNumber\":$i}" > /dev/null 2>&1
done

sleep 2

# Restart follower
echo -e "\n${YELLOW}Restarting follower...${NC}"
NODE_NUM=$((FOLLOWER_PORT - 8080))
nohup java -jar target/raft-cache-1.0-SNAPSHOT.jar --config config/node${NODE_NUM}.yaml > logs/node${NODE_NUM}_restart.log 2>&1 &
echo "Follower restarted"

# Wait for catchup
echo -e "\n${YELLOW}Waiting 10 seconds for follower to catch up...${NC}"
sleep 10

# Verify follower has ALL data
echo -e "\n${YELLOW}Verifying follower caught up...${NC}"
OLD_KEYS=0
NEW_KEYS=0

for i in {1..10}; do
    RESULT=$(curl -s "http://localhost:$FOLLOWER_PORT/cache/recovery_test_$i?consistency=eventual" 2>/dev/null | jq -r '.value' 2>/dev/null || echo "")
    if [ "$RESULT" == "val_$i" ]; then
        ((OLD_KEYS++))
    fi
done

for i in {31..40}; do
    RESULT=$(curl -s "http://localhost:$FOLLOWER_PORT/cache/recovery_test_$i?consistency=eventual" 2>/dev/null | jq -r '.value' 2>/dev/null || echo "")
    if [ "$RESULT" == "val_$i" ]; then
        ((NEW_KEYS++))
    fi
done

echo "Old keys (before crash): $OLD_KEYS/10"
echo "New keys (after crash): $NEW_KEYS/10"

# Summary
echo -e "\n${GREEN}=========================================="
echo "  Test Results"
echo "==========================================${NC}"

if [ "$OLD_KEYS" -ge 8 ] && [ "$NEW_KEYS" -ge 7 ]; then
    echo -e "${GREEN}✓ PASSED - Follower recovered and synced${NC}"
    ./scripts/stop-cluster.sh
    exit 0
else
    echo -e "${RED}✗ FAILED - Follower did not sync properly${NC}"
    ./scripts/stop-cluster.sh
    exit 1
fi
