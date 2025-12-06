#!/bin/bash

# Start Demo Script
# Launches 3 Raft Nodes, ML Service, and Frontend

# Colors
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}Starting Raft UI Demo...${NC}"

# 0. Clean previous state
echo "Cleaning up previous state..."
rm -rf data/*
echo "State cleared."

# 1. Start ML Service
echo "Starting ML Service..."
cd ml-service
source venv/bin/activate
python app.py > /tmp/ml-service.log 2>&1 &
ML_PID=$!
cd ..
echo -e "${GREEN}ML Service started (PID: $ML_PID)${NC}"

# 2. Start Raft Nodes
echo "Starting Raft Nodes..."
java -jar target/raft-cache-1.0-SNAPSHOT.jar --config config/node1.yaml > /tmp/node1.log 2>&1 &
N1_PID=$!
java -jar target/raft-cache-1.0-SNAPSHOT.jar --config config/node2.yaml > /tmp/node2.log 2>&1 &
N2_PID=$!
java -jar target/raft-cache-1.0-SNAPSHOT.jar --config config/node3.yaml > /tmp/node3.log 2>&1 &
N3_PID=$!
echo -e "${GREEN}Raft Nodes started (PIDs: $N1_PID, $N2_PID, $N3_PID)${NC}"

# 3. Start Frontend
echo "Starting Frontend..."
cd frontend
npm run dev &
FE_PID=$!
cd ..
echo -e "${GREEN}Frontend started (PID: $FE_PID)${NC}"

echo ""
echo -e "${CYAN}Demo is running!${NC}"
echo "Access UI at: http://localhost:5173"
echo ""
echo "Press Ctrl+C to stop all services..."

trap "kill $ML_PID $N1_PID $N2_PID $N3_PID $FE_PID; exit" INT
wait
