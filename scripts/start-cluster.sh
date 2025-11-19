#!/bin/bash
# ---------------------------------------------------------
# Start all 3 nodes in separate processes
# ---------------------------------------------------------

JAR_FILE="target/raft-cache-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
  echo "❌ Build not found! Run: mvn clean package -DskipTests"
  exit 1
fi

mkdir -p logs

echo "🚀 Starting Distributed Raft Cache cluster..."

java -jar $JAR_FILE --config src/main/resources/node-1-config.yaml > logs/node1.log 2>&1 &
PID1=$!
sleep 2

java -jar $JAR_FILE --config src/main/resources/node-2-config.yaml > logs/node2.log 2>&1 &
PID2=$!
sleep 2

java -jar $JAR_FILE --config src/main/resources/node-3-config.yaml > logs/node3.log 2>&1 &
PID3=$!

echo "✅ Cluster started:"
echo "Node 1 PID: $PID1"
echo "Node 2 PID: $PID2"
echo "Node 3 PID: $PID3"
echo
echo "Check logs in ./logs/ or stop cluster with ./scripts/stop-cluster.sh"
