#!/bin/bash

# Start Demo Script
# Launches 3 Raft Nodes, ML Service, and Frontend

# Colors
GREEN='\033[0;32m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}Starting Raft UI Demo...${NC}"

# 0. Cleanup previous state
echo "Cleaning up previous state..."
pkill -f "raft-cache" 2>/dev/null || true
pkill -f "python app.py" 2>/dev/null || true
rm -rf data/* logs/*
mkdir -p logs
mkdir -p data
echo "State cleared."

# 1. Build Project
echo "Building project... (this may take a moment)"
mvn clean package -DskipTests > logs/build.log 2>&1
if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed! Check logs/build.log${NC}"
    exit 1
fi
echo -e "${GREEN}Build successful.${NC}"

# 2. Start ML Service
echo "Starting ML Service..."
cd ml-service
if [ ! -d "venv" ]; then
    echo "Creating Python virtual environment..."
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt > ../logs/ml_install.log 2>&1
else
    source venv/bin/activate
fi
python app.py > ../logs/ml-service.log 2>&1 &
ML_PID=$!
cd ..
echo -e "${GREEN}ML Service started (PID: $ML_PID)${NC}"

# 3. Start Node Manager (starts/manages all nodes)
echo "Starting Node Manager..."
python node-manager.py > logs/node-manager.log 2>&1 &
NM_PID=$!
echo -e "${GREEN}Node Manager started (PID: $NM_PID)${NC}"
echo "Waiting for manager to initialize..."
sleep 2
echo "Triggering cluster startup..."
curl -X POST http://localhost:5002/start-all
echo ""

# 4. Start Frontend
echo "Starting Frontend..."
cd frontend
npm run dev > ../logs/frontend.log 2>&1 &
FE_PID=$!
cd ..
echo -e "${GREEN}Frontend started (PID: $FE_PID)${NC}"

echo ""
echo -e "${CYAN}Demo is running!${NC}"
echo "Access UI at: http://localhost:5173"
echo "Logs are available in the 'logs/' directory."
echo ""
echo "Press Ctrl+C to stop all services..."

trap "echo 'Stopping services...'; kill $ML_PID $NM_PID $FE_PID; exit" INT
wait
