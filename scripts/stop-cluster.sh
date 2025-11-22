#!/bin/bash
# Stop all Raft nodes
pkill -f "raft-cache" 2>/dev/null || true
sleep 1
echo "Cluster stopped"
