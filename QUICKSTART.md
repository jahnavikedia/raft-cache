# Quick Start Guide

## Extract and Open Project (5 minutes)

1. **Extract the archive**:
   ```bash
   tar -xzf raft-cache.tar.gz
   cd raft-cache
   ```

2. **Open in VS Code**:
   ```bash
   code .
   ```
   Or: File → Open Folder → select `raft-cache`

3. **Wait for indexing** (30 seconds): VS Code will detect the Maven project and download dependencies automatically.

## First Build (2 minutes)

In VS Code terminal (`` Ctrl+` ``):
```bash
mvn clean install
```

Expected output:
```
[INFO] BUILD SUCCESS
[INFO] Total time: 30s
```

## Run Your First Node (30 seconds)

```bash
mvn exec:java -Dexec.mainClass="com.distributed.cache.Main" -Dexec.args="node1 8001"
```

You should see:
```
INFO  c.d.cache.Main - Starting Distributed Raft Cache...
INFO  c.d.cache.raft.RaftNode - RaftNode initialized: id=node1, port=8001
INFO  c.d.cache.raft.RaftNode - Starting Raft node: node1
INFO  c.d.cache.Main - Node node1 started on port 8001
```

Press `Ctrl+C` to stop.

## Run Tests

```bash
mvn test
```

Expected:
```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## What You Have Now

✅ Complete Maven project structure  
✅ Core Raft classes (RaftNode, LogEntry, Message, RaftState)  
✅ Cache implementation (CacheStore, CacheEntry)  
✅ ML client stub (for future integration)  
✅ Logging configured  
✅ Basic unit tests  

## Next Steps (Week 1 Tasks)

1. **Implement Leader Election** (Day 1-2):
   - Add election timeout in RaftNode
   - Implement vote request/response logic
   - Test election with multiple nodes

2. **Add Network Layer** (Day 3-4):
   - Create Netty server/client in `network/` package
   - Implement message sending/receiving
   - Test communication between nodes

3. **Implement Heartbeat** (Day 5):
   - Add heartbeat timer
   - Send periodic AppendEntries (empty) from leader
   - Followers reset election timer on heartbeat

See README.md for complete roadmap!

## Files Overview

```
pom.xml                     → Maven configuration (dependencies)
SETUP.md                    → Detailed setup instructions
README.md                   → Project overview and roadmap

src/main/java/com/distributed/cache/
├── Main.java               → Entry point
├── raft/
│   ├── RaftNode.java       → Core node (TODO: add election logic)
│   ├── RaftState.java      → FOLLOWER/CANDIDATE/LEADER states
│   ├── LogEntry.java       → Represents log entries
│   └── Message.java        → Inter-node messages
├── cache/
│   ├── CacheStore.java     → Key-value storage
│   └── CacheEntry.java     → Cache entry with metadata
└── ml/
    └── MLClient.java       → ML service client (stub)

src/test/java/              → Your tests go here
```

## Common Commands

```bash
# Build
mvn clean install

# Run tests
mvn test

# Run single node
mvn exec:java -Dexec.mainClass="com.distributed.cache.Main" -Dexec.args="node1 8001"

# Package JAR
mvn package

# Run JAR
java -jar target/raft-cache-1.0-SNAPSHOT.jar node1 8001
```

## Tips

- **Keep it simple initially**: Focus on getting leader election working first
- **Test frequently**: Run `mvn test` after each feature
- **Use logging**: Check `logs/raft-cache.log` for debugging
- **Git commits**: Commit after each working feature
- **Ask questions**: If stuck, refer to SETUP.md or reach out

## Week 1 Goal

By end of week 1, you should have:
- Leader election working with 3 nodes
- Basic network communication
- Heartbeat mechanism preventing unnecessary elections

Good luck! 🚀
