# Quick Start - Test Your Raft Implementation

## 🎯 Three Ways to See Raft in Action

### 1️⃣ Run Tests (2 minutes)

```bash
mvn test -Dtest=RaftNodeElectionTest
```

**Expected Result:**
```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 2️⃣ Interactive Demo (Fun!)

```bash
# Compile first
mvn clean compile

# Run the demo
mvn exec:java -Dexec.mainClass="com.distributed.cache.demo.RaftDemo"
```

**What you'll see:**
- Create a 3-node cluster (Alice, Bob, Charlie)
- Watch leader election happen
- Kill the leader and see automatic re-election
- Real-time cluster status

**Demo Menu:**
```
Options:
  1 - Show cluster status
  2 - Show detailed statistics
  3 - Kill the leader (simulate failure)
  4 - Restart a dead node
  5 - Watch cluster for 10 seconds
  q - Quit
```

### 3️⃣ Quick Verification (30 seconds)

```bash
mvn clean compile && mvn test -Dtest=RaftNodeElectionTest#testElectionProducesLeader
```

Just runs one test to verify it works!

---

## 📊 What You'll Observe

### During Tests

**Phase 1: Startup**
```
[INFO] Starting Raft node: node1
[INFO] Server started on port 8001
[INFO] Connected to peer node2 at localhost:8002
```

**Phase 2: Election**
```
[WARN] Election timeout fired for node2
[INFO] Node node2 started election for term 1
[INFO] Node node1 GRANTED vote to node2
[INFO] Node node2 WON election with 2/3 votes!
[INFO] Node node2 transitioning to LEADER
```

**Phase 3: Heartbeats**
```
[DEBUG] Leader node2 sent heartbeat #1 to 2 peers
[DEBUG] Node node1 received AppendEntries from node2
[DEBUG] Node node3 received AppendEntries from node2
```

**Phase 4: Test Results**
```
[INFO] ✓ Election triggered after timeout. Elections started: 2
[INFO] ✓ Node node2 became leader
[INFO] ✓ Leader sent 39 heartbeats
[INFO] ✓ Followers received 39/39 heartbeats
[INFO] ✓ System stable: node2 is leader, others are followers
```

---

## 🎮 Try This Flow

1. **Start with tests** to verify everything works:
   ```bash
   mvn test -Dtest=RaftNodeElectionTest
   ```

2. **Run the demo** to see it visually:
   ```bash
   mvn exec:java -Dexec.mainClass="com.distributed.cache.demo.RaftDemo"
   ```

3. **In the demo, try this sequence:**
   - Type `1` - See Alice, Bob, Charlie status
   - Wait 2 seconds - Watch election happen automatically
   - Type `1` again - See who became leader
   - Type `5` - Watch cluster for 10 seconds (see heartbeats counting up)
   - Type `3` - Kill the leader
   - Type `1` - See new leader elected!
   - Type `q` - Quit

---

## ✅ Success Indicators

You'll know it's working when you see:

- ✅ Exactly **1 leader** elected
- ✅ Heartbeats sent every **~50ms**
- ✅ Followers receive heartbeats
- ✅ **No elections** while leader is healthy
- ✅ **Quick re-election** (<500ms) after leader failure
- ✅ All nodes on **same term**

---

## 🐛 Troubleshooting

**"Address already in use"**
```bash
# Wait 30 seconds, or kill processes on ports 8001-8003
lsof -ti:8001-8003 | xargs kill -9
```

**Demo won't start**
```bash
# Make sure it compiled
mvn clean compile
```

**Tests are slow**
- **Normal!** Tests wait for timeouts (150-300ms each)
- Full suite takes ~3 minutes

---

## 📖 Full Documentation

- **TESTING_GUIDE.md** - Complete testing documentation
- **DAY2_HEARTBEAT_IMPLEMENTATION.md** - Technical deep dive
- **QUICK_REFERENCE.md** - One-page summary for developers

---

## 🎉 That's It!

Your Raft consensus implementation is **fully working**!

**Next:** Week 2 will add log replication 🚀
