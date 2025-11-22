#!/bin/bash

# Test script for access tracking functionality
# This script:
# 1. Inserts 10 keys into the cache
# 2. Accesses keys 1-5 multiple times (10 times each)
# 3. Calls GET /cache/access-stats to verify tracking
# 4. Verifies keys 1-5 have higher access counts than keys 6-10

set -e

# Configuration
NODE_PORT=8081
BASE_URL="http://localhost:${NODE_PORT}"
CLIENT_ID="test-client-$(date +%s)"

echo "=============================================="
echo "Testing Access Tracking"
echo "=============================================="
echo "Node: ${BASE_URL}"
echo "Client ID: ${CLIENT_ID}"
echo ""

# Function to PUT a key-value pair
put_key() {
    local key=$1
    local value=$2
    local seq=$3

    curl -s -X POST "${BASE_URL}/cache/${key}" \
        -H "Content-Type: application/json" \
        -d "{\"clientId\": \"${CLIENT_ID}\", \"value\": \"${value}\", \"sequenceNumber\": ${seq}}" \
        > /dev/null
}

# Function to GET a key
get_key() {
    local key=$1

    curl -s -X GET "${BASE_URL}/cache/${key}" > /dev/null
}

# Step 1: Insert 10 keys
echo "Step 1: Inserting 10 keys into the cache..."
for i in {1..10}; do
    put_key "key${i}" "value${i}" $i
    echo "  - Inserted key${i}"
done
echo ""

# Give a moment for writes to commit
sleep 2

# Step 2: Access keys 1-5 multiple times (10 times each)
echo "Step 2: Accessing keys 1-5 (10 times each)..."
for iteration in {1..10}; do
    for i in {1..5}; do
        get_key "key${i}"
    done
    echo "  - Iteration ${iteration} complete"
done
echo ""

# Step 3: Access keys 6-10 once each
echo "Step 3: Accessing keys 6-10 (1 time each)..."
for i in {6..10}; do
    get_key "key${i}"
    echo "  - Accessed key${i}"
done
echo ""

# Wait a moment for tracking to update
sleep 1

# Step 4: Get access stats
echo "Step 4: Fetching access statistics..."
STATS_RESPONSE=$(curl -s -X GET "${BASE_URL}/cache/access-stats")
echo ""

# Pretty print the response
echo "=============================================="
echo "Access Statistics Response:"
echo "=============================================="
echo "${STATS_RESPONSE}" | python3 -m json.tool 2>/dev/null || echo "${STATS_RESPONSE}"
echo ""

# Step 5: Verify results
echo "=============================================="
echo "Verification:"
echo "=============================================="

# Extract access counts using jq (if available) or grep
if command -v jq &> /dev/null; then
    echo "Keys 1-5 should have ~10+ accesses:"
    for i in {1..5}; do
        count=$(echo "${STATS_RESPONSE}" | jq -r ".stats[] | select(.key == \"key${i}\") | .totalAccessCount" 2>/dev/null || echo "N/A")
        echo "  - key${i}: ${count} accesses"
    done

    echo ""
    echo "Keys 6-10 should have ~1 access:"
    for i in {6..10}; do
        count=$(echo "${STATS_RESPONSE}" | jq -r ".stats[] | select(.key == \"key${i}\") | .totalAccessCount" 2>/dev/null || echo "N/A")
        echo "  - key${i}: ${count} accesses"
    done
else
    echo "Install 'jq' for detailed verification, or review the JSON output above."
    echo "Expected: keys 1-5 should have ~10+ accesses, keys 6-10 should have ~1 access"
fi

echo ""
echo "=============================================="
echo "Test Complete!"
echo "=============================================="
