# Distributed Raft Cache

A distributed cache implementation using the Raft consensus algorithm with ML-based cache eviction policies.

## Project Overview

This project implements a distributed key-value cache with:
- **Raft Consensus**: Ensures consistency across multiple nodes
- **ML-Based Eviction**: Python microservice for intelligent cache eviction predictions
- **High Availability**: Leader election and log replication
- **Performance Optimization**: Based on "Rethink the Linearizability Constraints of Raft"

## Architecture

```
┌─────────────────────────────────────────┐
│         Java Raft Cache Cluster         │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐ │
│  │ Node 1  │  │ Node 2  │  │ Node 3  │ │
│  │(Leader) │  │(Follower)│ │(Follower)│ │
│  └────┬────┘  └────┬────┘  └────┬────┘ │
│       │            │             │       │
│       └────────────┴─────────────┘       │
│              Raft Protocol               │
└────────────────┬────────────────────────┘
                 │ HTTP REST
                 ↓
         ┌──────────────┐
         │   Python ML  │
         │   Service    │
         │  (Eviction)  │
         └──────────────┘
```

## Technology Stack

### Java Components
- **Java 17**: Core language
- **Maven**: Build and dependency management
- **Netty**: Network communication
- **Jackson**: JSON serialization
- **SLF4J + Logback**: Logging

### Python Components (Coming Soon)
- **Flask**: REST API for ML service
- **scikit-learn**: ML models for eviction prediction
- **Docker**: Containerization

## Project Structure

```
raft-cache/
├── src/
│   ├── main/java/com/distributed/cache/
│   │   ├── Main.java              # Entry point
│   │   ├── raft/                  # Raft consensus implementation
│   │   │   ├── RaftNode.java      # Core node logic
│   │   │   ├── RaftState.java     # Node states (Leader/Follower/Candidate)
│   │   │   ├── LogEntry.java      # Log entries
│   │   │   └── Message.java       # Inter-node messages
│   │   ├── cache/                 # Cache implementation
│   │   │   ├── CacheStore.java    # Key-value store
│   │   │   └── CacheEntry.java    # Cache entry with metadata
│   │   ├── ml/                    # ML integration
│   │   │   └── MLClient.java      # Client for Python ML service
│   │   └── network/               # Network layer (TODO)
│   ├── main/resources/
│   │   └── logback.xml            # Logging configuration
│   └── test/                      # Unit tests (TODO)
├── docker/                        # Docker configurations (TODO)
├── config/                        # Configuration files (TODO)
└── pom.xml                        # Maven configuration
```

## Getting Started

### Prerequisites
- Java 17 or later
- Maven 3.6+
- Visual Studio Code with Java extensions

### Setup in VS Code

1. **Open the project**:
   ```bash
   code /path/to/raft-cache
   ```

2. **Install VS Code extensions**:
   - Extension Pack for Java
   - Maven for Java
   - Debugger for Java

3. **Build the project**:
   ```bash
   mvn clean install
   ```

4. **Run a single node** (for testing):
   ```bash
   mvn exec:java -Dexec.mainClass="com.distributed.cache.Main" -Dexec.args="node1 8001"
   ```

### Running Multiple Nodes

To test the distributed system locally:

```bash
# Terminal 1 - Node 1
java -jar target/raft-cache-1.0-SNAPSHOT.jar node1 8001

# Terminal 2 - Node 2
java -jar target/raft-cache-1.0-SNAPSHOT.jar node2 8002

# Terminal 3 - Node 3
java -jar target/raft-cache-1.0-SNAPSHOT.jar node3 8003
```

## Development Roadmap

### Week 1: Core Raft Implementation
- [x] Project structure setup
- [x] Basic classes (RaftNode, LogEntry, Message)
- [ ] Leader election algorithm
- [ ] Heartbeat mechanism
- [ ] Network communication with Netty

### Week 2: Log Replication & Cache
- [ ] Log replication implementation
- [ ] Cache store with basic operations (PUT/GET/DELETE)
- [ ] State machine execution
- [ ] Persistence layer

### Week 3: ML Integration & Optimization
- [ ] Python ML service for eviction prediction
- [ ] HTTP client integration
- [ ] Performance optimizations from research paper
- [ ] Adaptive consistency levels

### Week 4: Testing & Documentation
- [ ] Comprehensive unit tests
- [ ] Integration tests
- [ ] Performance benchmarking vs Redis
- [ ] Final documentation

## Testing

Run all tests:
```bash
mvn test
```

Run specific test:
```bash
mvn test -Dtest=RaftNodeTest
```

## Configuration

Configuration files will be in `config/`:
- `cluster.yaml`: Cluster topology
- `cache.yaml`: Cache settings
- `ml.yaml`: ML service configuration

## Contributing

This is an academic project. For questions or suggestions, please open an issue.

## License

MIT License - Academic Project

## References

- "In Search of an Understandable Consensus Algorithm" (Raft paper)
- "Rethink the Linearizability Constraints of Raft for Distributed Key-Value Stores"
- IEEE Xplore research papers on distributed caching
