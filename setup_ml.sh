#!/bin/bash

# Setup script for ML-based cache eviction
# This script automates the setup process for the ML service

set -e

echo "=============================================="
echo "ML Cache Eviction Setup"
echo "=============================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if Python 3 is installed
echo "Checking dependencies..."
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}✗ Python 3 is not installed${NC}"
    echo "Please install Python 3.7 or higher"
    exit 1
fi

PYTHON_VERSION=$(python3 --version | cut -d' ' -f2)
echo -e "${GREEN}✓ Python ${PYTHON_VERSION} found${NC}"

# Check if we're in the right directory
if [ ! -d "ml-service" ]; then
    echo -e "${RED}✗ ml-service directory not found${NC}"
    echo "Please run this script from the raft-cache root directory"
    exit 1
fi

echo -e "${GREEN}✓ ml-service directory found${NC}"
echo ""

# Step 1: Create virtual environment
echo "Step 1: Creating Python virtual environment..."
cd ml-service

if [ -d "venv" ]; then
    echo -e "${YELLOW}⚠ Virtual environment already exists${NC}"
    read -p "Do you want to recreate it? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        rm -rf venv
        python3 -m venv venv
        echo -e "${GREEN}✓ Virtual environment recreated${NC}"
    else
        echo -e "${BLUE}→ Using existing virtual environment${NC}"
    fi
else
    python3 -m venv venv
    echo -e "${GREEN}✓ Virtual environment created${NC}"
fi
echo ""

# Step 2: Activate virtual environment and install dependencies
echo "Step 2: Installing Python dependencies..."
source venv/bin/activate

# Upgrade pip
pip install --upgrade pip > /dev/null 2>&1

# Install requirements
if pip install -r requirements.txt > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Dependencies installed successfully${NC}"
else
    echo -e "${RED}✗ Failed to install dependencies${NC}"
    exit 1
fi
echo ""

# Step 3: Train the model
echo "Step 3: Training ML model..."
if [ -f "cache_eviction_model.pkl" ]; then
    echo -e "${YELLOW}⚠ Model file already exists${NC}"
    read -p "Do you want to retrain it? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo ""
        python train_model.py
    else
        echo -e "${BLUE}→ Using existing model${NC}"
    fi
else
    echo ""
    python train_model.py
fi
echo ""

# Step 4: Verify setup
echo "Step 4: Verifying setup..."
CHECKS_PASSED=0
TOTAL_CHECKS=3

# Check 1: Virtual environment
if [ -d "venv" ]; then
    echo -e "${GREEN}✓ Virtual environment exists${NC}"
    ((CHECKS_PASSED++))
else
    echo -e "${RED}✗ Virtual environment missing${NC}"
fi

# Check 2: Dependencies
if python -c "import numpy, pandas, sklearn, flask, joblib" 2>/dev/null; then
    echo -e "${GREEN}✓ All dependencies installed${NC}"
    ((CHECKS_PASSED++))
else
    echo -e "${RED}✗ Some dependencies missing${NC}"
fi

# Check 3: Model file
if [ -f "cache_eviction_model.pkl" ]; then
    echo -e "${GREEN}✓ Model file exists${NC}"
    ((CHECKS_PASSED++))
else
    echo -e "${RED}✗ Model file missing${NC}"
fi

echo ""
echo "Verification: ${CHECKS_PASSED}/${TOTAL_CHECKS} checks passed"
echo ""

if [ $CHECKS_PASSED -eq $TOTAL_CHECKS ]; then
    echo "=============================================="
    echo -e "${GREEN}Setup Complete!${NC}"
    echo "=============================================="
    echo ""
    echo "Next steps:"
    echo ""
    echo "1. Start the ML service:"
    echo "   cd ml-service"
    echo "   source venv/bin/activate"
    echo "   python app.py"
    echo ""
    echo "2. In another terminal, test the service:"
    echo "   cd ml-service"
    echo "   ./test_ml_service.sh"
    echo ""
    echo "3. Start a Raft node and test access tracking:"
    echo "   mvn clean package"
    echo "   java -jar target/raft-cache-1.0-SNAPSHOT.jar node1 8081"
    echo "   ./scripts/test_access_tracking.sh"
    echo ""
    echo "For more information, see:"
    echo "  - QUICKSTART_ML.md - Quick start guide"
    echo "  - ml-service/README.md - ML service documentation"
    echo "  - ML_IMPLEMENTATION_SUMMARY.md - Implementation details"
    echo ""
else
    echo "=============================================="
    echo -e "${RED}Setup Failed!${NC}"
    echo "=============================================="
    echo ""
    echo "Please check the error messages above and try again."
    echo ""
    exit 1
fi
