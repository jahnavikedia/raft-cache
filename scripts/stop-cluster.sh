#!/bin/bash
# ---------------------------------------------------------
# Gracefully stop all running Raft Cache nodes
# ---------------------------------------------------------

echo "🛑 Stopping all Distributed Raft Cache nodes..."

PIDS=$(ps aux | grep 'raft-cache-1.0-SNAPSHOT.jar' | grep -v grep | awk '{print $2}')

if [ -z "$PIDS" ]; then
  echo "No running nodes found."
else
  echo "Killing PIDs:"
  echo "$PIDS"
  kill $PIDS
  sleep 1
  echo "✅ All nodes stopped."
fi
