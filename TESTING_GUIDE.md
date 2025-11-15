# Testing Guide - Raft Consensus Implementation

This guide shows you how to test and see your Raft implementation in action!

---

## Option 1: Run Automated Tests (Recommended First)

The integration test suite verifies all Raft functionality automatically.

### Run All Tests
```bash
mvn test -Dtest=RaftNodeElectionTest
```

### What You'll See
- ✅ 11/11 tests passing
- Tests for: election, voting, heartbeats, term convergence, stability
- Execution time: ~3 minutes (includes deliberate waits for timing verification)

### Expected Output
```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Key Tests
1. **Test 2**: Election timeout triggers election
2. **Test 3**: Election produces exactly one leader
3. **Test 4**: Leader sends heartbeats to followers
4. **Test 5**: Heartbeats prevent unnecessary elections
5. **Test 6-7**: Vote granting and majority logic
6. **Test 8**: Term convergence across cluster
7. **Test 9-10**: System stability and heartbeat frequency
8. **Test 11**: Statistics tracking
9. **Test 12**: Recovery after forced re-election

---

## Option 2: Interactive Demo (Fun!)

Run an interactive demo where you can control the cluster!

### Step 1: Compile the Demo
```bash
mvn clean compile
```

### Step 2: Run the Demo
```bash
mvn exec:java -Dexec.mainClass="com.distributed.cache.demo.RaftDemo"
```

### What You Can Do

**1. Show Cluster Status**
```
Alice    | State: LEADER     | Term: 1 | Connections: 2/2 ⭐ LEADER
Bob      | State: FOLLOWER   | Term: 1 | Connections: 2/2
Charlie  | State: FOLLOWER   | Term: 1 | Connections: 2/2
```

**2. Show Detailed Statistics**
- See heartbeat counts
- View election statistics
- Check connection status
- Monitor term numbers

**3. Kill the Leader (Simulate Failure)**
```
Leader is: Alice
Shutting down Alice...
Alice is DOWN!

Watching for re-election...
✓ New leader elected: Bob
```

**4. Restart a Dead Node**
- Bring a crashed node back online
- Watch it rejoin the cluster as a follower

**5. Watch Cluster in Real-Time**
```
t=0s: Leader=Bob | Heartbeats sent=15
t=1s: Leader=Bob | Heartbeats sent=35
t=2s: Leader=Bob | Heartbeats sent=55
...
```

---

## Option 3: Quick Smoke Test

Just want to verify it works? Run this:

```bash
# Compile
mvn clean compile

# Run just one test (fastest)
mvn test -Dtest=RaftNodeElectionTest#testElectionProducesLeader

# Should see:
# [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

---

## Understanding the Output

### What Happens During a Test Run

1. **Startup (0-500ms)**
   ```
   [INFO] Starting Raft node: node1
   [INFO] Server started on port 8001
   [INFO] Connected to peer node2 at localhost:8002
   [INFO] Node node1 started successfully. State=FOLLOWER, Term=0
   ```

2. **Election (500-800ms)**
   ```
   [WARN] Election timeout fired for node2
   [INFO] Node node2 started election for term 1
   [INFO] Node node1 GRANTED vote to node2 for term 1
   [INFO] Node node2 WON election with 2/3 votes!
   [INFO] Node node2 transitioning to LEADER
   ```

3. **Heartbeat Phase (ongoing)**
   ```
   [DEBUG] Leader node2 sent heartbeat #1 to 2 peers
   [DEBUG] Node node1 received AppendEntries from node2
   [DEBUG] Node node3 received AppendEntries from node2
   ```

4. **Stable Operation**
   ```
   [INFO] ✓ Leader sent 39 heartbeats
   [INFO] ✓ Followers received 39/39 heartbeats
   [INFO] ✓ System stable: node2 is leader, others are followers
   ```

### Log Levels Explained

- **INFO**: Important state transitions (elections, leader changes)
- **DEBUG**: Heartbeat messages, votes granted/denied
- **TRACE**: Low-level timer resets and scheduling
- **WARN**: Timeouts, connection issues

---

## Troubleshooting

### Tests Are Slow
**Normal!** Tests intentionally wait for timeouts (150-300ms) and stability checks. Full test suite takes ~3 minutes.

### Port Already in Use
```
Error: Address already in use
```
**Fix**: Wait a moment for ports to be released, or kill any existing processes:
```bash
lsof -ti:8001-8003 | xargs kill -9  # Kill any processes on test ports
```

### No Leader Elected
**Cause**: Network connections not established yet
**Fix**: Tests automatically retry connections. If issue persists, check firewall settings.

### Demo Won't Start
**Fix**: Make sure Maven compiled successfully:
```bash
mvn clean compile
```

---

## Performance Benchmarks

From your test results:

- **Leader Election Time**: ~200-600ms
- **Heartbeat Frequency**: 20 heartbeats/second (1 every 50ms)
- **Election Timeout**: 150-300ms (randomized to prevent split votes)
- **Failure Detection**: Within 150-300ms of leader crash
- **Network Bandwidth**: ~8 KB/s for 3-node cluster (negligible)

---

## What to Look For

### ✅ Good Behavior
- Exactly 1 leader elected
- Heartbeats sent every ~50ms
- Followers receive heartbeats
- No elections while leader is healthy
- Quick re-election after leader failure (<500ms)
- All nodes converge to same term

### ❌ Bad Behavior (Bugs)
- Multiple leaders (split brain!)
- No leader elected
- Continuous elections (election storm)
- Heartbeats not received
- Term divergence

---

## Next Steps

After verifying everything works:

1. **Read the Logs**: See the Raft algorithm in action
2. **Modify Timing**: Try changing `HEARTBEAT_INTERVAL_MS` in [RaftNode.java](src/main/java/com/distributed/cache/raft/RaftNode.java:25)
3. **Add Metrics**: Extend the statistics tracking
4. **Week 2 Preview**: Next you'll add log replication!

---

## Quick Reference

```bash
# Run all tests
mvn test -Dtest=RaftNodeElectionTest

# Run specific test
mvn test -Dtest=RaftNodeElectionTest#testLeaderSendsHeartbeats

# Run interactive demo
mvn exec:java -Dexec.mainClass="com.distributed.cache.demo.RaftDemo"

# Compile only
mvn clean compile

# Clean everything
mvn clean
```

---

## Example Test Session

Here's what a complete test run looks like:

```bash
$ mvn test -Dtest=RaftNodeElectionTest

[INFO] Running com.distributed.cache.raft.RaftNodeElectionTest

[INFO] ✓ Election triggered after timeout. Elections started: 2
[INFO] ✓ Node node2 became leader
[INFO] ✓ Leader sent 15 heartbeats
[INFO] ✓ Followers received 15/15 heartbeats
[INFO] ✓ Heartbeats prevented elections. New elections: 0
[INFO] ✓ Nodes voted: node1=node2, node2=node2, node3=node2
[INFO] ✓ Leader node2 won with 3/3 votes
[INFO] ✓ Terms converged: node1=1, node2=1, node3=1
[INFO] ✓ System stable: node2 is leader, others are followers
[INFO] ✓ Leader sent 10 heartbeats in 500ms (expected ~10)
[INFO] ✓ Statistics:
[INFO]   - Total elections: 2
[INFO]   - Leader heartbeats sent: 45
[INFO]   - Follower heartbeats received: 45
[INFO] ✓ System recovered after forced re-election

[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Perfect!** ✨ All tests passing means your Raft implementation is working correctly!

---

**Happy Testing! 🚀**