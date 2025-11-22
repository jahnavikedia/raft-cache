#!/bin/bash

# Quick Raft KV Store Test
# Run this after starting the cluster with ./scripts/start-cluster.sh

echo "=== Quick Raft KV Store Test ==="
echo ""

# Find the leader
echo "1. Finding the leader..."
for port in 8081 8082 8083; do
    role=$(curl -s http://localhost:$port/status 2>/dev/null | jq -r '.role' 2>/dev/null)
    if [ "$role" = "LEADER" ]; then
        LEADER=$port
        echo "   Leader is on port $LEADER"
        break
    fi
done

if [ -z "$LEADER" ]; then
    echo "   ERROR: No leader found!"
    exit 1
fi

echo ""
echo "2. Checking cluster status..."
for port in 8081 8082 8083; do
    status=$(curl -s http://localhost:$port/status 2>/dev/null | jq -c '{nodeId, role, term: .currentTerm, commitIndex, logSize}')
    echo "   Port $port: $status"
done

echo ""
echo "3. Testing PUT operation..."
result=$(curl -s -X POST -H 'Content-Type: application/json' \
    -d '{"value":"TestValue","clientId":"quicktest","sequenceNumber":1}' \
    http://localhost:$LEADER/cache/test:key | jq -c)
echo "   Result: $result"

echo ""
echo "4. Waiting for replication (2 seconds)..."
sleep 2

echo ""
echo "5. Verifying replication on all nodes..."
for port in 8081 8082 8083; do
    value=$(curl -s http://localhost:$port/cache/test:key 2>/dev/null | jq -r '.value')
    echo "   Port $port: value = $value"
done

echo ""
echo "6. Final cluster state..."
for port in 8081 8082 8083; do
    status=$(curl -s http://localhost:$port/status 2>/dev/null | jq -c '{nodeId, commitIndex, logSize}')
    echo "   Port $port: $status"
done

echo ""
echo "=== Test Complete ==="