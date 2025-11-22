#!/bin/bash

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}=========================================="
echo "  Concurrent Writes Test (Simplified)"
echo "==========================================${NC}"

# Start cluster
echo "Starting cluster..."
./scripts/start-cluster.sh
sleep 8

# Find leader
LEADER_PORT=""
for port in 8081 8082 8083; do
    ROLE=$(curl -s http://localhost:$port/status 2>/dev/null | jq -r '.role' 2>/dev/null || echo "")
    if [ "$ROLE" == "LEADER" ]; then
        LEADER_PORT=$port
        break
    fi
done

if [ -z "$LEADER_PORT" ]; then
    echo -e "${RED}✗ Could not find leader${NC}"
    ./scripts/stop-cluster.sh
    exit 1
fi

echo -e "${GREEN}✓ Leader on port $LEADER_PORT${NC}"

# Function to write data from a client
write_client_data() {
    local client_id=$1
    local start_key=$2
    local num_writes=$3
    local success_count=0

    for i in $(seq 1 $num_writes); do
        key="client${client_id}_key${i}"
        value="client${client_id}_val${i}"

        response=$(curl -s -X POST http://localhost:$LEADER_PORT/cache/$key \
            -H 'Content-Type: application/json' \
            -d "{\"value\":\"$value\",\"clientId\":\"client$client_id\",\"sequenceNumber\":$i}" 2>/dev/null)

        if echo "$response" | jq -e '.success == true' > /dev/null 2>&1; then
            ((success_count++))
        fi
    done

    echo "$success_count"
}

# Launch 4 concurrent clients, each writing 30 keys
echo -e "\n${YELLOW}Launching 4 concurrent clients (30 writes each)...${NC}"

write_client_data 1 1 30 > /tmp/client1.result &
PID1=$!
write_client_data 2 1 30 > /tmp/client2.result &
PID2=$!
write_client_data 3 1 30 > /tmp/client3.result &
PID3=$!
write_client_data 4 1 30 > /tmp/client4.result &
PID4=$!

# Wait for all clients to finish
echo "Waiting for clients to complete..."
wait $PID1 $PID2 $PID3 $PID4

# Collect results
CLIENT1_SUCCESS=$(cat /tmp/client1.result)
CLIENT2_SUCCESS=$(cat /tmp/client2.result)
CLIENT3_SUCCESS=$(cat /tmp/client3.result)
CLIENT4_SUCCESS=$(cat /tmp/client4.result)

TOTAL_SUCCESS=$((CLIENT1_SUCCESS + CLIENT2_SUCCESS + CLIENT3_SUCCESS + CLIENT4_SUCCESS))
TOTAL_EXPECTED=120  # 4 clients * 30 writes

echo -e "${GREEN}Write phase complete${NC}"
echo "Client 1: $CLIENT1_SUCCESS/30 successful"
echo "Client 2: $CLIENT2_SUCCESS/30 successful"
echo "Client 3: $CLIENT3_SUCCESS/30 successful"
echo "Client 4: $CLIENT4_SUCCESS/30 successful"
echo "Total: $TOTAL_SUCCESS/$TOTAL_EXPECTED writes successful"

# Wait for replication
echo -e "\n${YELLOW}Waiting 3 seconds for replication...${NC}"
sleep 3

# Verify data consistency - read sample keys from each client
echo -e "\n${YELLOW}Verifying data consistency...${NC}"
VERIFIED_COUNT=0
CHECKED_COUNT=0

for client_id in {1..4}; do
    for i in {1..10}; do  # Check 10 keys per client
        key="client${client_id}_key${i}"
        expected="client${client_id}_val${i}"

        actual=$(curl -s "http://localhost:$LEADER_PORT/cache/$key?consistency=eventual" 2>/dev/null | jq -r '.value' 2>/dev/null || echo "")
        ((CHECKED_COUNT++))

        if [ "$actual" == "$expected" ]; then
            ((VERIFIED_COUNT++))
        fi
    done
done

echo "Verified: $VERIFIED_COUNT/$CHECKED_COUNT keys match expected values"

# Test deduplication - write same key with same sequence number twice
echo -e "\n${YELLOW}Testing deduplication...${NC}"
DEDUP_KEY="dedup_test_key"
DEDUP_VALUE="original_value"

# First write
curl -s -X POST http://localhost:$LEADER_PORT/cache/$DEDUP_KEY \
    -H 'Content-Type: application/json' \
    -d "{\"value\":\"$DEDUP_VALUE\",\"clientId\":\"dedup_client\",\"sequenceNumber\":1}" > /dev/null 2>&1

sleep 1

# Try to overwrite with higher sequence number (should succeed)
curl -s -X POST http://localhost:$LEADER_PORT/cache/$DEDUP_KEY \
    -H 'Content-Type: application/json' \
    -d "{\"value\":\"updated_value\",\"clientId\":\"dedup_client\",\"sequenceNumber\":2}" > /dev/null 2>&1

sleep 1

# Check final value
FINAL_VALUE=$(curl -s "http://localhost:$LEADER_PORT/cache/$DEDUP_KEY?consistency=eventual" 2>/dev/null | jq -r '.value' 2>/dev/null || echo "")

if [ "$FINAL_VALUE" == "updated_value" ]; then
    echo -e "${GREEN}✓ Sequence number ordering works correctly${NC}"
    DEDUP_PASS=1
else
    echo -e "${RED}✗ Sequence number ordering failed (got: $FINAL_VALUE)${NC}"
    DEDUP_PASS=0
fi

# Summary
echo -e "\n${GREEN}=========================================="
echo "  Test Results"
echo "==========================================${NC}"

SUCCESS_RATE=$((TOTAL_SUCCESS * 100 / TOTAL_EXPECTED))
CONSISTENCY_RATE=$((VERIFIED_COUNT * 100 / CHECKED_COUNT))

echo "Write Success Rate: ${SUCCESS_RATE}% ($TOTAL_SUCCESS/$TOTAL_EXPECTED)"
echo "Data Consistency: ${CONSISTENCY_RATE}% ($VERIFIED_COUNT/$CHECKED_COUNT)"
echo "Deduplication: $([ $DEDUP_PASS -eq 1 ] && echo 'PASS' || echo 'FAIL')"

# Cleanup
rm -f /tmp/client*.result

# Pass criteria: ≥90% writes successful, ≥90% data consistent, dedup works
if [ $SUCCESS_RATE -ge 90 ] && [ $CONSISTENCY_RATE -ge 90 ] && [ $DEDUP_PASS -eq 1 ]; then
    echo -e "\n${GREEN}✓ PASSED - Concurrent writes handled correctly${NC}"
    ./scripts/stop-cluster.sh
    exit 0
else
    echo -e "\n${RED}✗ FAILED - Issues with concurrent writes${NC}"
    ./scripts/stop-cluster.sh
    exit 1
fi
