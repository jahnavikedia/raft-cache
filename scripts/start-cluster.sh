#!/bin/bash
# Start 3-node Raft cluster

pkill -f "raft-cache" 2>/dev/null || true
sleep 2

# Clean up old data to avoid snapshot/log mismatch
rm -rf data logs
mkdir -p logs data/node1 data/node2 data/node3

java -jar target/raft-cache-1.0-SNAPSHOT.jar --config config/node1.yaml > logs/node1.log 2>&1 &
java -jar target/raft-cache-1.0-SNAPSHOT.jar --config config/node2.yaml > logs/node2.log 2>&1 &
java -jar target/raft-cache-1.0-SNAPSHOT.jar --config config/node3.yaml > logs/node3.log 2>&1 &

echo "Cluster starting..."
sleep 5
echo "Cluster ready"
