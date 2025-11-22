#!/bin/bash

# Comprehensive Raft Key-Value Store Test Script

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
total_tests=0
passed_tests=0

# Helper functions
print_header() {
    echo -e "\n${BLUE}================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================================${NC}"
}

print_test() {
    echo -e "\n${YELLOW}Test $1: $2${NC}"
}

run_test() {
    local test_name=$1
    local test_cmd=$2
    local expected=$3

    total_tests=$((total_tests + 1))
    print_test "$total_tests" "$test_name"

    result=$(eval "$test_cmd" 2>&1)

    if echo "$result" | grep -q "$expected"; then
        echo -e "${GREEN}✓ PASS${NC}"
        passed_tests=$((passed_tests + 1))
        return 0
    else
        echo -e "${RED}✗ FAIL${NC}"
        echo "  Expected: $expected"
        echo "  Got: $result"
        return 1
    fi
}

# Find the leader
find_leader() {
    for port in 8081 8082 8083; do
        role=$(curl -s http://localhost:$port/status 2>/dev/null | jq -r '.role' 2>/dev/null)
        if [ "$role" = "LEADER" ]; then
            echo $port
            return 0
        fi
    done
    echo "8081" # Default fallback
}

# Wait for condition
wait_for() {
    local max_wait=$1
    local check_cmd=$2
    local deadline=$(($(date +%s) + max_wait))

    while [ $(date +%s) -lt $deadline ]; do
        if eval "$check_cmd" > /dev/null 2>&1; then
            return 0
        fi
        sleep 0.5
    done
    return 1
}

# Main test suite
print_header "Raft Key-Value Store - Comprehensive Test Suite"

# Test 1: Check cluster status
print_header "1. CLUSTER STATUS CHECK"

for port in 8081 8082 8083; do
    node_num=$((port - 8080))
    echo -e "\nNode $node_num (port $port):"
    status=$(curl -s http://localhost:$port/status 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "$status" | jq '{nodeId, role, term: .currentTerm, commitIndex, logSize}'
        echo -e "${GREEN}✓ Node $node_num is running${NC}"
    else
        echo -e "${RED}✗ Node $node_num is not responding${NC}"
    fi
done

# Find the leader
LEADER_PORT=$(find_leader)
echo -e "\n${GREEN}Leader is on port: $LEADER_PORT${NC}"

# Test 2: Basic PUT operation
print_header "2. BASIC OPERATIONS"

run_test "PUT operation on leader" \
    "curl -s -X POST -H 'Content-Type: application/json' -d '{\"value\":\"Alice\",\"clientId\":\"test\",\"sequenceNumber\":1}' http://localhost:$LEADER_PORT/cache/user:1" \
    '"success":true'

sleep 1

run_test "GET operation on leader" \
    "curl -s http://localhost:$LEADER_PORT/cache/user:1" \
    '"value":"Alice"'

# Test 3: Replication verification
print_header "3. REPLICATION VERIFICATION"

echo "Waiting 2 seconds for replication..."
sleep 2

for port in 8081 8082 8083; do
    node_num=$((port - 8080))
    run_test "Replication to node $node_num" \
        "curl -s http://localhost:$port/cache/user:1" \
        '"value":"Alice"'
done

# Test 4: Multiple PUT operations
print_header "4. MULTIPLE OPERATIONS"

run_test "PUT user:2" \
    "curl -s -X POST -H 'Content-Type: application/json' -d '{\"value\":\"Bob\",\"clientId\":\"test\",\"sequenceNumber\":2}' http://localhost:$LEADER_PORT/cache/user:2" \
    '"success":true'

run_test "PUT user:3" \
    "curl -s -X POST -H 'Content-Type: application/json' -d '{\"value\":\"Charlie\",\"clientId\":\"test\",\"sequenceNumber\":3}' http://localhost:$LEADER_PORT/cache/user:3" \
    '"success":true'

sleep 2

run_test "GET user:2 from leader" \
    "curl -s http://localhost:$LEADER_PORT/cache/user:2" \
    '"value":"Bob"'

run_test "GET user:3 from leader" \
    "curl -s http://localhost:$LEADER_PORT/cache/user:3" \
    '"value":"Charlie"'

# Test 5: DELETE operation
print_header "5. DELETE OPERATIONS"

run_test "DELETE user:1" \
    "curl -s -X DELETE -H 'Content-Type: application/json' -d '{\"clientId\":\"test\",\"sequenceNumber\":4}' http://localhost:$LEADER_PORT/cache/user:1" \
    '"success":true'

sleep 2

run_test "GET deleted key (should not exist)" \
    "curl -s http://localhost:$LEADER_PORT/cache/user:1" \
    '"success":false'

# Test 6: Verify commitIndex is advancing
print_header "6. COMMIT INDEX VERIFICATION"

for port in 8081 8082 8083; do
    node_num=$((port - 8080))
    commit_index=$(curl -s http://localhost:$port/status 2>/dev/null | jq -r '.commitIndex')
    log_size=$(curl -s http://localhost:$port/status 2>/dev/null | jq -r '.logSize')

    echo "Node $node_num: commitIndex=$commit_index, logSize=$log_size"

    if [ "$commit_index" -gt 0 ]; then
        echo -e "${GREEN}✓ Node $node_num has commitIndex > 0${NC}"
        passed_tests=$((passed_tests + 1))
    else
        echo -e "${RED}✗ Node $node_num has commitIndex = 0${NC}"
    fi
    total_tests=$((total_tests + 1))
done

# Test 7: Deduplication test
print_header "7. DEDUPLICATION TEST"

run_test "First PUT with seq=100" \
    "curl -s -X POST -H 'Content-Type: application/json' -d '{\"value\":\"Original\",\"clientId\":\"dedup\",\"sequenceNumber\":100}' http://localhost:$LEADER_PORT/cache/dedup:test" \
    '"success":true'

sleep 1

run_test "Duplicate PUT with same seq=100" \
    "curl -s -X POST -H 'Content-Type: application/json' -d '{\"value\":\"Duplicate\",\"clientId\":\"dedup\",\"sequenceNumber\":100}' http://localhost:$LEADER_PORT/cache/dedup:test" \
    '"success":true'

sleep 1

run_test "Value should still be 'Original'" \
    "curl -s http://localhost:$LEADER_PORT/cache/dedup:test" \
    '"value":"Original"'

# Test 8: Stress test - multiple operations
print_header "8. STRESS TEST (20 operations)"

echo "Writing 20 keys..."
for i in {1..20}; do
    curl -s -X POST -H 'Content-Type: application/json' \
        -d "{\"value\":\"Value$i\",\"clientId\":\"stress\",\"sequenceNumber\":$i}" \
        http://localhost:$LEADER_PORT/cache/stress:$i > /dev/null 2>&1
    echo -n "."
done
echo " Done!"

echo "Waiting for replication..."
sleep 3

# Verify a few random keys
run_test "Verify stress:5" \
    "curl -s http://localhost:$LEADER_PORT/cache/stress:5" \
    '"value":"Value5"'

run_test "Verify stress:10" \
    "curl -s http://localhost:$LEADER_PORT/cache/stress:10" \
    '"value":"Value10"'

run_test "Verify stress:20" \
    "curl -s http://localhost:$LEADER_PORT/cache/stress:20" \
    '"value":"Value20"'

# Test 9: Check log consistency across nodes
print_header "9. LOG CONSISTENCY CHECK"

echo "Checking if all nodes have the same log size and commitIndex..."
log_sizes=()
commit_indices=()

for port in 8081 8082 8083; do
    status=$(curl -s http://localhost:$port/status 2>/dev/null)
    log_size=$(echo "$status" | jq -r '.logSize')
    commit_index=$(echo "$status" | jq -r '.commitIndex')
    log_sizes+=($log_size)
    commit_indices+=($commit_index)
done

# Check if all log sizes are equal
if [ "${log_sizes[0]}" = "${log_sizes[1]}" ] && [ "${log_sizes[1]}" = "${log_sizes[2]}" ]; then
    echo -e "${GREEN}✓ All nodes have the same log size: ${log_sizes[0]}${NC}"
    passed_tests=$((passed_tests + 1))
else
    echo -e "${RED}✗ Log sizes differ: ${log_sizes[*]}${NC}"
fi
total_tests=$((total_tests + 1))

# Check if all commit indices are equal
if [ "${commit_indices[0]}" = "${commit_indices[1]}" ] && [ "${commit_indices[1]}" = "${commit_indices[2]}" ]; then
    echo -e "${GREEN}✓ All nodes have the same commitIndex: ${commit_indices[0]}${NC}"
    passed_tests=$((passed_tests + 1))
else
    echo -e "${RED}✗ Commit indices differ: ${commit_indices[*]}${NC}"
fi
total_tests=$((total_tests + 1))

# Final summary
print_header "TEST SUMMARY"

echo -e "\nTotal Tests: $total_tests"
echo -e "Passed: ${GREEN}$passed_tests${NC}"
echo -e "Failed: ${RED}$((total_tests - passed_tests))${NC}"

if [ $passed_tests -eq $total_tests ]; then
    echo -e "\n${GREEN}🎉 ALL TESTS PASSED! 🎉${NC}"
    exit 0
else
    echo -e "\n${RED}⚠️  SOME TESTS FAILED${NC}"
    echo -e "Pass rate: $(echo "scale=1; $passed_tests * 100 / $total_tests" | bc)%"
    exit 1
fi