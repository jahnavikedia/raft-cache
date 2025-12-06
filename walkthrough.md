# Raft UI Demo Walkthrough

This walkthrough guides you through the new Raft UI Demo, which visualizes the distributed consensus algorithm and ML-based cache eviction.

## Prerequisites

- Java 17+
- Python 3.9+
- Node.js 18+

## Quick Start

Run the all-in-one demo script:

```bash
./start_demo.sh
```

This will:

1.  Start the **ML Service** (Port 5001)
2.  Start **3 Raft Nodes** (Ports 8081, 8082, 8083)
3.  Start the **Frontend** (Port 5173)

Open your browser to [http://localhost:5173](http://localhost:5173).

## Demo Features

### 1. Cluster Visualization

- **Nodes**: See the state of all 3 nodes (Leader, Follower, Candidate).
- **Term & Log**: Real-time view of the current term and commit index.
- **Read Lease**: Visual indicator when a node holds a valid read lease (Green badge).

### 2. Interactive Controls

- **Write**: Send a key-value pair to the cluster.
- **Strong Read**: Perform a standard Raft read (goes through Leader).
- **Lease Read**: Perform a read using the "Read Lease" optimization (faster, local read).
- **Generate Traffic**: Simulate access patterns to train the ML model.

### 3. ML-Driven Eviction

- **Analyze Eviction**: Click the button to get a recommendation from the ML service.
- **Visual Confidence**: See the probability score for the recommended eviction candidate.

## Troubleshooting

- If the UI doesn't load, ensure port 5173 is free.
- If nodes show "DOWN", check the terminal output for Java errors.
- If ML stats fail, ensure the Python virtual environment is set up correctly (`cd ml-service && source venv/bin/activate`).
