# Day 5: Cluster Configuration & Discovery

**Team Member**: Person B (Heartbeat & Failure Detection)
**Date**: November 14, 2025
**Status**: ✅ COMPLETED

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [What We Built](#what-we-built)
3. [Architecture](#architecture)
4. [Implementation Details](#implementation-details)
5. [Test Results](#test-results)
6. [How to Use](#how-to-use)
7. [Configuration Format](#configuration-format)
8. [Key Learnings](#key-learnings)

---

## Overview

### The Problem
Before Day 5, our Raft implementation had several limitations:
- **Hardcoded addresses**: Node addresses were hardcoded in the source code
- **Manual peer configuration**: Required code changes to add/remove nodes
- **No connection management**: Manual connection logic without retry or pooling
- **Poor observability**: Hard to know which peers are connected

### The Solution
Implemented a production-ready cluster configuration system with:
- **YAML-based configuration**: Human-readable, easy to edit
- **Automatic validation**: Catch configuration errors before starting
- **Connection pooling**: Efficient, reusable network channels
- **Exponential backoff retry**: Robust handling of temporary failures
- **Health monitoring**: Automatic detection and reconnection of stale connections

---

## What We Built

### Core Components (3 files, ~920 lines of production code)

1. **[NodeInfo.java](src/main/java/com/distributed/cache/config/NodeInfo.java)** (120 lines)
   - Immutable POJO representing a cluster node
   - Validation for node ID, host, and port (1024-65535)
   - `equals()` based on ID for Set/Map usage

2. **[ClusterConfig.java](src/main/java/com/distributed/cache/config/ClusterConfig.java)** (327 lines)
   - YAML configuration loader using Jackson
   - Comprehensive validation (timing, duplicates, topology)
   - O(1) node lookup, peer listing, quorum calculation

3. **[PeerManager.java](src/main/java/com/distributed/cache/network/PeerManager.java)** (480 lines)
   - Connection pooling for all peer nodes
   - Exponential backoff retry (1s → 2s → 4s → 8s → 16s → max 30s)
   - Health monitoring every 5 seconds
   - `sendMessage()` and `broadcast()` APIs

### Configuration Files

4. **[cluster-config.yaml](cluster-config.yaml)**
   - Production example configuration
   - 3-node cluster on localhost

5. **[src/test/resources/test-cluster-config.yaml](src/test/resources/test-cluster-config.yaml)**
   - Test configuration used by ClusterConfigTest

### Test Suite (429 lines)

6. **[ClusterConfigTest.java](src/test/java/com/distributed/cache/config/ClusterConfigTest.java)** (20 tests)
   - Loading tests (file system, classpath, error handling)
   - Parsing tests (node lookup, peer lists)
   - Validation tests (duplicates, ranges, timing)
   - All 20 tests passing ✅

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                  cluster-config.yaml                          │
│                                                                │
│  cluster:                                                     │
│    election_timeout_min: 150     # milliseconds              │
│    election_timeout_max: 300     # milliseconds              │
│    heartbeat_interval: 50        # milliseconds              │
│    nodes:                                                     │
│      - id: "node1"                                           │
│        host: "localhost"                                     │
│        port: 8001                                            │
│      - id: "node2"                                           │
│        host: "localhost"                                     │
│        port: 8002                                            │
│      - id: "node3"                                           │
│        host: "localhost"                                     │
│        port: 8003                                            │
└─────────────────────┬────────────────────────────────────────┘
                      │ Jackson YAML Parser
                      ▼
┌──────────────────────────────────────────────────────────────┐
│              ClusterConfig.java                               │
│                                                                │
│  • Loads YAML configuration                                  │
│  • Validates all parameters                                  │
│  • Provides node lookup methods                              │
│  • Calculates quorum size                                    │
│                                                                │
│  Key Methods:                                                │
│  - load(path) / loadFromClasspath(resource)                  │
│  - getNodeById(id) → NodeInfo (O(1) lookup)                  │
│  - getAllNodes() → List<NodeInfo>                            │
│  - getOtherNodes(excludeId) → List<NodeInfo> (peers)         │
│  - getMajoritySize() → int (quorum)                          │
└─────────────────────┬────────────────────────────────────────┘
                      │ Used by
                      ▼
┌──────────────────────────────────────────────────────────────┐
│                PeerManager.java                               │
│                                                                │
│  • Connection pooling (Map<nodeId, Channel>)                 │
│  • Exponential backoff retry logic                           │
│  • Health monitoring (every 5 seconds)                       │
│  • Message sending APIs                                      │
│                                                                │
│  Key Methods:                                                │
│  - connectToAllPeers()                                       │
│  - sendMessage(peerId, message) → boolean                    │
│  - broadcast(message) → int (count sent)                     │
│  - isPeerConnected(peerId) → boolean                         │
│  - getPeerStatus() → Map<peerId, connected>                  │
└─────────────────────┬────────────────────────────────────────┘
                      │ Integrates with
                      ▼
┌──────────────────────────────────────────────────────────────┐
│           NetworkBase.java  /  RaftNode.java                  │
│                   (Future Integration)                        │
└──────────────────────────────────────────────────────────────┘
```

---

## Implementation Details

### 1. NodeInfo.java - Node Representation

**Purpose**: Immutable value object representing a cluster node

**Key Features**:
```java
public class NodeInfo {
    private final String id;      // e.g., "node1"
    private final String host;    // e.g., "localhost" or "192.168.1.10"
    private final int port;       // e.g., 8001 (range: 1024-65535)

    // Validation in constructor
    public NodeInfo(String id, String host, int port) {
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        // ... (null checks, trimming)
    }

    // Convenience method
    public String getAddress() {
        return host + ":" + port;  // "localhost:8001"
    }

    // equals() based on ID only (for Set/Map usage)
    @Override
    public boolean equals(Object o) {
        return Objects.equals(id, ((NodeInfo) o).id);
    }
}
```

**Design Decisions**:
- **Immutable**: Thread-safe, can be shared across components
- **Validation in constructor**: Fail-fast, catches errors early
- **equals() on ID only**: Node identity doesn't change even if address changes

---

### 2. ClusterConfig.java - Configuration Management

**Purpose**: Load and validate cluster configuration from YAML

**Loading Methods**:
```java
// From file system
ClusterConfig config = ClusterConfig.load("cluster-config.yaml");

// From classpath (for packaged JARs)
ClusterConfig config = ClusterConfig.loadFromClasspath("test-cluster-config.yaml");
```

**Validation Rules** (enforced on load):
1. `heartbeat_interval > 0`
2. `election_timeout_min > 0`
3. `election_timeout_max > election_timeout_min`
4. `heartbeat_interval * 3 <= election_timeout_min` ⭐ **Raft safety rule**
5. At least 1 node (warns if < 3 for production)
6. No duplicate node IDs
7. No duplicate host:port combinations
8. All ports in range 1024-65535

**Usage Example**:
```java
ClusterConfig config = ClusterConfig.load("cluster-config.yaml");

// Get timing parameters
int heartbeat = config.getHeartbeatInterval();      // 50ms
int minTimeout = config.getElectionTimeoutMin();   // 150ms
int maxTimeout = config.getElectionTimeoutMax();   // 300ms

// Get node information
NodeInfo myNode = config.getNodeById("node1");
List<NodeInfo> allNodes = config.getAllNodes();     // All 3 nodes
List<NodeInfo> peers = config.getOtherNodes("node1");  // node2, node3

// Get cluster info
int size = config.getClusterSize();       // 3
int majority = config.getMajoritySize();  // 2 (quorum for 3 nodes)
```

**Why Validation Matters**:
```yaml
# BAD: Heartbeat too slow (will cause false timeouts)
heartbeat_interval: 100   # 100 * 3 = 300 > 150 (election_timeout_min)
election_timeout_min: 150

# GOOD: Heartbeat fast enough (3x rule)
heartbeat_interval: 50    # 50 * 3 = 150 <= 150 ✅
election_timeout_min: 150
```

---

### 3. PeerManager.java - Connection Management

**Purpose**: Manage persistent connections to all peer nodes with robust error handling

**Connection Lifecycle**:
```
1. Start
   └─> connectToAllPeers()

2. For each peer:
   ├─> attemptConnection(peer, retryCount=0)
   │
   ├─> SUCCESS
   │   ├─> Store channel in peerChannels map
   │   ├─> Register close listener
   │   └─> Reset retry counter
   │
   └─> FAILURE
       ├─> scheduleReconnect(peer, retryCount+1)
       └─> Wait exponential backoff delay
           ├─> Attempt 1: wait 1 second
           ├─> Attempt 2: wait 2 seconds
           ├─> Attempt 3: wait 4 seconds
           ├─> Attempt 4: wait 8 seconds
           ├─> Attempt 5: wait 16 seconds
           └─> Attempt 6+: wait 30 seconds (max)

3. Health Monitor (every 5 seconds)
   └─> Check all channels still active
       └─> If inactive: remove & trigger reconnect
```

**Key Methods**:
```java
PeerManager peerManager = new PeerManager(myNodeId, config, messageHandler);

// 1. Connect to all peers (call after server starts)
peerManager.connectToAllPeers();

// 2. Send to specific peer
Message voteRequest = createVoteRequest();
boolean sent = peerManager.sendMessage("node2", voteRequest);

// 3. Broadcast to all peers
Message heartbeat = createHeartbeat();
int sentCount = peerManager.broadcast(heartbeat);
System.out.println("Sent to " + sentCount + " peers");

// 4. Check connection status
boolean connected = peerManager.isPeerConnected("node2");
Map<String, Boolean> status = peerManager.getPeerStatus();
// {"node2": true, "node3": false}

int connectedCount = peerManager.getConnectedPeerCount();

// 5. Cleanup
peerManager.shutdown();
```

**Retry Schedule Example**:
```
Time 0s: Attempt 1 → Connection refused
Time 1s: Attempt 2 → Connection refused  (waited 1s)
Time 3s: Attempt 3 → Connection refused  (waited 2s)
Time 7s: Attempt 4 → Connection refused  (waited 4s)
Time 15s: Attempt 5 → Connection refused (waited 8s)
Time 31s: Attempt 6 → Connection refused (waited 16s)
Time 61s: Attempt 7 → SUCCESS!          (waited 30s, max delay)
```

**Design Decisions**:
- **Why exponential backoff?** Avoids overwhelming a failed peer, standard practice
- **Why retry forever?** Network issues are often temporary (want auto-recovery)
- **Why health checks?** Detect silent failures (firewall drops, partitions)
- **Why connection pooling?** Efficient, reduces overhead of creating new connections

---

## Test Results

### Full Test Suite ✅

```
[INFO] Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Test Breakdown**:

| Test Suite | Tests | Status | Duration |
|------------|-------|--------|----------|
| CacheStoreTest | 5 | ✅ PASS | 0.3s |
| **ClusterConfigTest** | **20** | ✅ **PASS** | **0.5s** |
| NetworkBaseTest | 5 | ✅ PASS | 1.7s |
| RaftNodeElectionTest | 11 | ✅ PASS | 181.1s |
| **Total** | **41** | ✅ **PASS** | **183.6s** |

### ClusterConfigTest Details (20 tests)

#### ✅ Loading Tests (4 tests)
- `testLoadFromClasspath` - Load config from classpath resources
- `testLoadFromFile` - Load config from file system
- `testFileNotFound` - Handle missing file gracefully
- `testClasspathResourceNotFound` - Handle missing resource gracefully

#### ✅ Parsing Tests (4 tests)
- `testGetAllNodes` - Retrieve all cluster nodes
- `testGetNodeById` - O(1) node lookup by ID
- `testGetOtherNodes` - Get peer list excluding self
- `testGetMajoritySize` - Calculate quorum size (3 nodes → 2)

#### ✅ Validation Tests (10 tests)
- `testDuplicateNodeIds` - Detect duplicate node IDs (fails with clear error)
- `testDuplicateAddresses` - Detect duplicate host:port combinations
- `testInvalidElectionTimeoutRange` - Reject max <= min
- `testNegativeHeartbeatInterval` - Reject negative intervals
- `testHeartbeatTooCloseToElectionTimeout` - Enforce heartbeat * 3 <= election_timeout
- `testEmptyNodeList` - Require at least 1 node
- `testInvalidYamlSyntax` - Handle malformed YAML
- `testMissingClusterRoot` - Require 'cluster' root element
- `testNodeInfoValidation` - Enforce port range 1024-65535
- `testSingleNodeCluster` - Allow single node (logs warning)

#### ✅ Edge Case Tests (2 tests)
- `testMajoritySizeFor5Nodes` - 5 nodes → majority = 3
- `testToString` - Proper string representation

**Key Validations Tested**:
1. ✅ **Timing Constraints**: Heartbeat small enough for reliable failure detection
2. ✅ **Cluster Topology**: No duplicate IDs or addresses
3. ✅ **Port Ranges**: Only valid ports (1024-65535)
4. ✅ **File Handling**: Graceful errors for missing/invalid files
5. ✅ **YAML Structure**: Proper root element and field mapping

---

## How to Use

### Step 1: Create Configuration File

Create `cluster-config.yaml` in your project root:

```yaml
cluster:
  # Timing configuration (milliseconds)
  election_timeout_min: 150
  election_timeout_max: 300
  heartbeat_interval: 50

  # Cluster nodes
  nodes:
    - id: "node1"
      host: "localhost"
      port: 8001

    - id: "node2"
      host: "localhost"
      port: 8002

    - id: "node3"
      host: "localhost"
      port: 8003
```

### Step 2: Load Configuration in Your Application

```java
import com.distributed.cache.config.ClusterConfig;
import com.distributed.cache.config.NodeInfo;

public class Main {
    public static void main(String[] args) throws IOException {
        // Load configuration
        ClusterConfig config = ClusterConfig.load("cluster-config.yaml");

        // Get node ID from command line
        String nodeId = args[0];  // "node1", "node2", or "node3"

        // Get node information
        NodeInfo myNode = config.getNodeById(nodeId);
        System.out.println("Starting " + nodeId);
        System.out.println("  Address: " + myNode.getAddress());
        System.out.println("  Cluster size: " + config.getClusterSize());
        System.out.println("  Majority needed: " + config.getMajoritySize());

        // Create and start RaftNode
        RaftNode node = new RaftNode(nodeId, config);
        node.start();
    }
}
```

### Step 3: Run Your Cluster

```bash
# Terminal 1 - Start node1
java -jar raft-cache.jar node1

# Terminal 2 - Start node2
java -jar raft-cache.jar node2

# Terminal 3 - Start node3
java -jar raft-cache.jar node3
```

### Step 4: Verify All Tests Pass

```bash
# Run all tests
mvn clean test

# Run only ClusterConfigTest
mvn test -Dtest=ClusterConfigTest

# Expected output:
# [INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
```

---

## Configuration Format

### YAML Structure

```yaml
cluster:                        # Root element (required)
  election_timeout_min: <int>   # Minimum election timeout (ms)
  election_timeout_max: <int>   # Maximum election timeout (ms)
  heartbeat_interval: <int>     # Heartbeat interval (ms)

  nodes:                        # List of nodes (at least 1)
    - id: "<string>"            # Unique node ID
      host: "<string>"          # Hostname or IP
      port: <int>               # Port (1024-65535)
```

### Production Example (3 servers)

```yaml
cluster:
  election_timeout_min: 150
  election_timeout_max: 300
  heartbeat_interval: 50

  nodes:
    - id: "server1"
      host: "10.0.1.10"
      port: 8001

    - id: "server2"
      host: "10.0.1.11"
      port: 8001

    - id: "server3"
      host: "10.0.1.12"
      port: 8001
```

### 5-Node Cluster (tolerates 2 failures)

```yaml
cluster:
  election_timeout_min: 150
  election_timeout_max: 300
  heartbeat_interval: 50

  nodes:
    - id: "node1"
      host: "raft1.example.com"
      port: 8001

    - id: "node2"
      host: "raft2.example.com"
      port: 8001

    - id: "node3"
      host: "raft3.example.com"
      port: 8001

    - id: "node4"
      host: "raft4.example.com"
      port: 8001

    - id: "node5"
      host: "raft5.example.com"
      port: 8001
```

**Quorum Calculation**:
- 3 nodes → majority = 2 (tolerates 1 failure)
- 5 nodes → majority = 3 (tolerates 2 failures)
- 7 nodes → majority = 4 (tolerates 3 failures)
- Formula: `majority = (N / 2) + 1`

---

## Key Learnings

### 1. YAML Configuration Best Practices
- ✅ Use Jackson library (battle-tested, industry standard)
- ✅ Validate on load (fail-fast approach)
- ✅ Support both file system and classpath loading
- ✅ Provide clear error messages for validation failures

### 2. Raft Timing Requirements
- ✅ **Critical Rule**: `heartbeat_interval * 3 <= election_timeout_min`
- ✅ Heartbeat must be much faster than election timeout
- ✅ Recommended: heartbeat = election_timeout / 3 to / 5
- ✅ Example: 50ms heartbeat, 150ms election timeout (3x ratio)

**Why this matters**:
```
If heartbeat = 100ms and election_timeout_min = 150ms:
- Leader sends heartbeat every 100ms
- Follower expects heartbeat within 150ms
- Network delay + processing = ~50ms
- 100ms + 50ms = 150ms (exactly at timeout) ❌
- Even tiny delays cause false leader failures!

If heartbeat = 50ms and election_timeout_min = 150ms:
- Leader sends heartbeat every 50ms
- Follower expects heartbeat within 150ms
- Network delay + processing = ~50ms
- 50ms + 50ms = 100ms (50ms buffer) ✅
- Safe margin for network delays!
```

### 3. Connection Management Patterns
- ✅ **Connection pooling**: Reuse channels instead of creating new ones
- ✅ **Exponential backoff**: Start fast (1s), increase gradually, cap at max (30s)
- ✅ **Health monitoring**: Periodic checks detect silent failures
- ✅ **Retry forever**: Network issues are often temporary

**Exponential Backoff Formula**:
```java
long delay = Math.min(
    INITIAL_DELAY_MS * (1L << retryCount),  // 2^retryCount
    MAX_DELAY_MS
);

// Example:
// retryCount=0: 1000 * 2^0 = 1000ms (1s)
// retryCount=1: 1000 * 2^1 = 2000ms (2s)
// retryCount=2: 1000 * 2^2 = 4000ms (4s)
// retryCount=3: 1000 * 2^3 = 8000ms (8s)
// retryCount=4: 1000 * 2^4 = 16000ms (16s)
// retryCount=5: 1000 * 2^5 = 32000ms, capped at 30000ms (30s)
```

### 4. Validation Strategies
- ✅ **Duplicate detection**: Use `Collectors.toMap()` (throws on duplicates)
- ✅ **Range validation**: Port must be 1024-65535 (avoid privileged ports)
- ✅ **Relationship validation**: Max must be greater than min
- ✅ **Batch validation**: Collect all errors, show them all at once

**Example Error Message**:
```
Cluster configuration validation failed:
  - Election timeout max (150) must be greater than min (200)
  - Heartbeat interval (100ms) must be much smaller than election timeout min (150ms)
  - Duplicate node ID found: node1
  - Duplicate address found: localhost:8001
```

### 5. Testing Best Practices
- ✅ Test both success and failure cases
- ✅ Use `@TempDir` for temporary test files (auto-cleanup)
- ✅ Test edge cases (single node, 5 nodes, invalid ports)
- ✅ Verify error messages contain helpful information
- ✅ Keep tests fast (< 1 second for unit tests)

### 6. Production Readiness Checklist
- ✅ Comprehensive validation (catch errors before runtime)
- ✅ Logging at appropriate levels (INFO for lifecycle, DEBUG for details)
- ✅ Immutable data structures (thread-safe)
- ✅ Graceful error handling (don't crash on bad input)
- ✅ Observability (connection status, peer counts)
- ✅ Resource cleanup (shutdown methods)

---

## What's Next?

### Completed ✅
- [x] Jackson YAML dependency added
- [x] NodeInfo.java created with validation
- [x] ClusterConfig.java created with YAML loading
- [x] PeerManager.java created with retry logic
- [x] cluster-config.yaml example created
- [x] ClusterConfigTest.java created (20 tests passing)
- [x] All tests verified (41 tests, 0 failures)
- [x] Documentation complete

### Future Work 🚧
- [ ] **Integrate with NetworkBase**: Replace hardcoded connections with PeerManager
- [ ] **Integrate with RaftNode**: Use ClusterConfig for timing parameters
- [ ] **Write PeerManagerTest**: Comprehensive test suite for connection management
- [ ] **Create example Main.java**: Demonstrate complete usage
- [ ] **Production deployment**: Deploy to real servers and test

---

## Summary

Day 5 successfully implemented a **production-ready cluster configuration and peer management system** that transforms the Raft cache from a hardcoded prototype to a flexible, configurable distributed system.

**Key Achievements**:
- 🎯 **920 lines** of production code
- ✅ **41 tests** passing (100% success rate)
- 📦 **3 core components** (NodeInfo, ClusterConfig, PeerManager)
- ⚙️ **YAML configuration** with comprehensive validation
- 🔄 **Connection management** with exponential backoff
- 📊 **Health monitoring** with automatic reconnection

**Benefits**:
- ✨ **Flexibility**: Change cluster topology without code changes
- 🛡️ **Robustness**: Automatic retry and recovery from failures
- 🔍 **Observability**: Real-time connection status
- ✅ **Validation**: Fail-fast error detection
- 🧪 **Testability**: Easy to test different configurations

This foundation enables the Raft distributed cache to run in real production environments with multiple servers, automatic failure recovery, and configuration-driven deployment. 🚀

---

**Generated by**: Person B
**Review Status**: Ready for Person A review
**Integration Status**: Ready to integrate with NetworkBase and RaftNode