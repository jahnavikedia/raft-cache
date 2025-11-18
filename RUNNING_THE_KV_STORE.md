# Running the Raft Key-Value Store

This guide explains how to run and test the Raft-based distributed key-value store implementation.

## Prerequisites

- Java 17 or higher
- Maven 3.6+ (if available)

## Building the Project

If Maven is installed:
```bash
mvn clean package
```

This will:
1. Compile all source files
2. Run all tests
3. Create a JAR file in `target/raft-cache-1.0-SNAPSHOT.jar`

## Option 1: Running the JUnit Test

The easiest way to test the implementation is to run the automated test:

```bash
mvn test -Dtest=ManualReplicationTest
```

This test:
1. Starts 3 Raft nodes (node1, node2, node3)
2. Waits for leader election
3. Performs PUT operations on the leader
4. Waits for replication
5. Verifies all nodes have the replicated values
6. Tests multiple operations

**Expected Output:**
```
=== Test: Log Replication ===
Leader elected: node1
Performing PUT operation: key1 = value1
PUT operation completed successfully
Waiting 2 seconds for replication...
Verifying replication on all nodes...
Node1 has key1 = value1
Node2 has key1 = value1
Node3 has key1 = value1
SUCCESS: Log replication working!
```

## Option 2: Running the Interactive Demo

You can also run an interactive demo with 3 nodes in separate terminals.

### Terminal 1 - Start Node 1:
```bash
java -cp target/raft-cache-1.0-SNAPSHOT.jar com.distributed.cache.demo.KVStoreDemo node1 7001
```

### Terminal 2 - Start Node 2:
```bash
java -cp target/raft-cache-1.0-SNAPSHOT.jar com.distributed.cache.demo.KVStoreDemo node2 7002
```

### Terminal 3 - Start Node 3:
```bash
java -cp target/raft-cache-1.0-SNAPSHOT.jar com.distributed.cache.demo.KVStoreDemo node3 7003
```

### Wait for Leader Election

After starting all 3 nodes, wait ~2-5 seconds for leader election. You'll see log messages indicating which node became the leader.

### Interactive Commands

On **any node** (but write operations only work on the leader):

```
# Insert a key-value pair (only on leader)
PUT mykey myvalue

# Retrieve a value (works on any node)
GET mykey

# Delete a key (only on leader)
DELETE mykey

# Show node status
STATUS

# Show help
HELP

# Exit
QUIT
```

### Example Session

**On the leader node:**
```
[node1:LEADER] > PUT name Alice
Executing PUT name = Alice
SUCCESS: name = Alice

[node1:LEADER] > PUT age 30
Executing PUT age = 30
SUCCESS: age = 30

[node1:LEADER] > STATUS
===== Node Status =====
Node ID: node1
State: LEADER
Term: 1
Voted For: node1
Commit Index: 3
Log Size: 3
Last Applied: 3
Connected Peers: 2
KV Store Size: 2
======================
```

**On a follower node:**
```
[node2:FOLLOWER] > GET name
name = Alice

[node2:FOLLOWER] > PUT city NYC
ERROR: This node is not the leader. Please try on the leader node.

[node2:FOLLOWER] > STATUS
===== Node Status =====
Node ID: node2
State: FOLLOWER
Term: 1
Voted For: node1
Commit Index: 3
Log Size: 3
Last Applied: 3
Connected Peers: 2
KV Store Size: 2
======================
```

## Verifying Replication

To verify that data is replicated across all nodes:

1. On the **leader**, execute: `PUT test replicated`
2. Wait ~2 seconds for replication
3. On **each follower**, execute: `GET test`
4. You should see `test = replicated` on all nodes

## Understanding the Logs

### Important Log Messages

**Leader Election:**
```
Node node1 WON election with 2/3 votes!
Node node1 transitioning to LEADER (term=1)
```

**Log Replication (Leader):**
```
Leader appended NO_OP entry at index 1
Sent 1 entries to follower node2 (nextIndex=2)
Follower node2 matched up to index 2
Advanced commitIndex to 2 (replicated on 3/3 nodes)
```

**Log Replication (Follower):**
```
Handling AppendEntries: prevLogIndex=1, entries=1
Appended 1 new entries to log
Updated commitIndex from 1 to 2
Applied entry 2 to state machine
Applied PUT: key='test', value='replicated'
```

## Data Persistence

All data is persisted to disk in the `data/` directory:

```
data/
├── node-node1/
│   ├── raft.log           # Append-only log entries (JSON)
│   └── state.json         # Current term and votedFor
├── node-node2/
│   ├── raft.log
│   └── state.json
└── node-node3/
    ├── raft.log
    └── state.json
```

### Testing Crash Recovery

1. Start all 3 nodes
2. On the leader, execute several PUT commands
3. Wait for replication (check STATUS to see commitIndex advancing)
4. **Kill one follower** (Ctrl+C)
5. On the leader, execute more PUT commands
6. **Restart the killed follower**
7. The follower will:
   - Load its log from disk
   - Reconnect to the cluster
   - Receive missing entries from the leader
   - Catch up to the current state

## Troubleshooting

### No Leader Elected

**Symptoms:** All nodes remain in CANDIDATE or FOLLOWER state

**Possible Causes:**
- Nodes can't connect to each other (firewall, wrong ports)
- Not enough nodes started (need majority: 2 out of 3)

**Solution:**
- Check that all 3 nodes are running
- Verify ports 7001, 7002, 7003 are not in use
- Check logs for connection errors

### "Not the leader" Error on PUT

**Symptoms:** `ERROR: This node is not the leader`

**Solution:**
- Check which node is the leader using `STATUS` command
- Execute write operations (PUT/DELETE) only on the leader
- Read operations (GET) work on any node

### Entries Not Replicating

**Symptoms:** Leader shows higher commitIndex than followers

**Possible Causes:**
- Network issues between nodes
- Followers not connected

**Solution:**
- Check `Connected Peers` in STATUS output (should be 2)
- Verify all nodes are running
- Check logs for AppendEntries success/failure messages

### Log Files Growing Large

The `raft.log` files grow with every operation. In production, you would implement:
- Log compaction/snapshotting
- Garbage collection of old entries
- Periodic snapshots of state machine

For this demo, you can safely delete the `data/` directory to start fresh.

## Performance Characteristics

### Latency
- **Write operations (PUT/DELETE):** 50-200ms
  - Time for leader to replicate to majority and commit
  - Dominated by replication interval (50ms) and network RTT

- **Read operations (GET):** <1ms
  - Local read from in-memory HashMap
  - No consensus required

### Throughput
- **Writes:** ~20-50 ops/sec (limited by replication interval)
- **Reads:** Thousands of ops/sec (limited by CPU/memory)

### Tuning Parameters

In the source code, you can adjust:
- `REPLICATION_INTERVAL_MS` in LeaderReplicator.java (default: 50ms)
  - Lower = faster commit, higher network traffic
  - Higher = slower commit, lower network traffic

- `APPLY_ENTRIES_INTERVAL` in RaftNode.java (default: 100ms)
  - How often followers apply committed entries

## Next Steps

After successfully running the basic key-value store:

1. **Add more operations:**
   - CAS (Compare-And-Swap)
   - Range queries
   - Batch operations

2. **Improve persistence:**
   - Log compaction
   - Snapshotting
   - State machine checkpointing

3. **Add client library:**
   - HTTP/REST API
   - gRPC interface
   - Client-side retry logic

4. **Implement cluster reconfiguration:**
   - Add/remove nodes dynamically
   - Use CONFIGURATION log entries

5. **Add monitoring:**
   - Metrics (throughput, latency)
   - Health checks
   - Dashboard

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                    RaftNode                         │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │   Election  │  │   Heartbeat  │  │   Apply    │ │
│  │    Timer    │  │    Timer     │  │   Timer    │ │
│  └─────────────┘  └──────────────┘  └────────────┘ │
│         │                 │                 │       │
│         ▼                 ▼                 ▼       │
│  ┌──────────────────────────────────────────────┐  │
│  │           State (FOLLOWER/CANDIDATE/LEADER)  │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   RaftLog    │  │ KeyValueStore│  │  NetworkBase │
│              │  │              │  │              │
│ - entries[]  │  │ - data (map) │  │ - channels   │
│ - commitIdx  │  │ - sequences  │  │ - handlers   │
│ - lastApplied│  │              │  │              │
└──────┬───────┘  └──────┬───────┘  └──────────────┘
       │                 │
       ▼                 ▼
┌──────────────┐  ┌──────────────┐
│LogPersistence│  │  Replicator  │
│              │  │              │
│ - raft.log   │  │ - Leader     │
│ - append()   │  │ - Follower   │
│ - load()     │  │              │
└──────────────┘  └──────────────┘
```

## Additional Resources

- [Raft Paper](https://raft.github.io/raft.pdf) - Original Raft consensus algorithm
- [Raft Visualization](https://raft.github.io/) - Interactive visualization
- [Implementation Summary](DAY6_IMPLEMENTATION_SUMMARY.md) - Detailed implementation notes
