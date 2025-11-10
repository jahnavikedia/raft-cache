# Team Division Guide - Two Developers

## Team Structure

**Person A (Raft Core Developer)** - Focus on consensus algorithm  
**Person B (Infrastructure Developer)** - Focus on networking and cache

Both work on separate branches, merge regularly.

---

## Week 1: Parallel Development

### Person A: Raft Consensus Logic
**Goal**: Implement the core Raft state machine

#### Day 1-2: Election Logic
- [ ] Add election timer to `RaftNode.java`
- [ ] Implement `startElection()` method
- [ ] Implement `handleRequestVote()` method
- [ ] Implement `handleRequestVoteResponse()` method
- [ ] Add vote counting logic
- [ ] Add transition to LEADER when majority votes received
- [ ] Write unit tests for election logic

**Files to Work On**:
- `src/main/java/com/distributed/cache/raft/RaftNode.java`
- `src/test/java/com/distributed/cache/raft/RaftNodeTest.java` (create this)

**Branch**: `feature/election-logic`

#### Day 3-4: Heartbeat Mechanism
- [ ] Add heartbeat timer for leaders
- [ ] Implement `sendHeartbeats()` method
- [ ] Implement `handleAppendEntries()` for followers
- [ ] Add logic to reset election timer on heartbeat
- [ ] Add logic to step down if higher term discovered
- [ ] Write tests for heartbeat behavior

**Files to Work On**:
- `src/main/java/com/distributed/cache/raft/RaftNode.java`
- `src/test/java/com/distributed/cache/raft/HeartbeatTest.java` (create this)

**Branch**: `feature/heartbeat`

#### Day 5-7: Term Management
- [ ] Add persistent term storage
- [ ] Implement term comparison logic
- [ ] Handle term updates from messages
- [ ] Add state transitions based on term
- [ ] Write integration tests

**Files to Work On**:
- `src/main/java/com/distributed/cache/raft/RaftNode.java`
- `src/main/java/com/distributed/cache/raft/PersistentState.java` (create this)

**Branch**: `feature/term-management`

---

### Person B: Network & Infrastructure
**Goal**: Build communication layer and testing infrastructure

#### Day 1-2: Network Server
- [ ] Create `NetworkServer.java` with Netty
- [ ] Implement server bootstrap and initialization
- [ ] Create `MessageHandler` interface
- [ ] Create `MessageHandlerAdapter` for Netty
- [ ] Add JSON serialization/deserialization
- [ ] Test server can accept connections
- [ ] Write unit tests

**Files to Create**:
- `src/main/java/com/distributed/cache/network/NetworkServer.java`
- `src/main/java/com/distributed/cache/network/MessageHandler.java`
- `src/main/java/com/distributed/cache/network/MessageHandlerAdapter.java`
- `src/test/java/com/distributed/cache/network/NetworkServerTest.java`

**Branch**: `feature/network-server`

#### Day 3-4: Network Client
- [ ] Create `NetworkClient.java` with Netty
- [ ] Implement client bootstrap and connection
- [ ] Add method to send messages to other nodes
- [ ] Add connection pooling/management
- [ ] Handle connection failures gracefully
- [ ] Test client can send messages
- [ ] Write unit tests

**Files to Create**:
- `src/main/java/com/distributed/cache/network/NetworkClient.java`
- `src/main/java/com/distributed/cache/network/ConnectionManager.java`
- `src/test/java/com/distributed/cache/network/NetworkClientTest.java`

**Branch**: `feature/network-client`

#### Day 5-7: Cluster Configuration & Testing
- [ ] Create cluster configuration file (`cluster.yaml`)
- [ ] Create `ClusterConfig.java` to read config
- [ ] Add node discovery/registration
- [ ] Create testing scripts for multi-node setup
- [ ] Create Docker Compose file for local testing
- [ ] Integration test for 3-node cluster
- [ ] Write documentation

**Files to Create**:
- `config/cluster.yaml`
- `src/main/java/com/distributed/cache/config/ClusterConfig.java`
- `docker/docker-compose.yml`
- `scripts/test-cluster.sh`
- `TESTING.md`

**Branch**: `feature/cluster-config`

---

## Daily Sync Schedule

### Daily Standup (15 min) - 9:00 AM
Both meet to discuss:
1. What did you complete yesterday?
2. What will you work on today?
3. Any blockers?

### Code Review Time (30 min) - 6:00 PM
- Review each other's pull requests
- Discuss integration points
- Plan next day

### Integration Time (1 hour) - End of Day 2, 4, 7
Merge branches and test together:
- Day 2: Integrate election logic + network server
- Day 4: Integrate heartbeat + network client
- Day 7: Full integration test of all components

---

## Git Workflow

### Setup (Do This First - Together)
```bash
# Person A
git clone <repo-url>
cd raft-cache
git checkout -b feature/election-logic

# Person B
git clone <repo-url>
cd raft-cache
git checkout -b feature/network-server
```

### Daily Workflow
```bash
# Morning - Pull latest from main
git checkout main
git pull origin main
git checkout <your-branch>
git merge main

# Throughout day - Commit frequently
git add .
git commit -m "Implemented election timer"

# Evening - Push your work
git push origin <your-branch>

# Create Pull Request on GitHub
```

### Integration Points
When Person A and B need to integrate:
```bash
# Person A merges first
git checkout main
git merge feature/election-logic
git push origin main

# Person B updates and merges
git checkout main
git pull origin main
git checkout feature/network-server
git merge main
# Resolve any conflicts
git checkout main
git merge feature/network-server
git push origin main
```

---

## Communication Protocol

### When to Communicate
- **Before changing shared files** (like `RaftNode.java`, `Message.java`)
- **After completing a major task** (send a quick message)
- **When stuck for > 30 minutes** (ask for help)
- **Before merging to main** (coordinate timing)

### Shared Files (Coordinate Changes)
- `RaftNode.java` - Both will modify
- `Message.java` - Both will modify
- `Main.java` - Both may modify

**Strategy**: 
- Person A focuses on the logic/state inside RaftNode
- Person B focuses on the network integration inside RaftNode
- Communicate before making big changes

---

## Week 1 Integration Milestones

### Milestone 1 (End of Day 2)
**Goal**: Single node can start election
- Person A: Election logic works (tested with mocks)
- Person B: Network server accepts connections
- **Together**: Test that server starts when node starts

### Milestone 2 (End of Day 4)
**Goal**: Two nodes can communicate
- Person A: Heartbeat logic implemented
- Person B: Client can send messages to server
- **Together**: Test message exchange between 2 nodes

### Milestone 3 (End of Day 7)
**Goal**: 3-node cluster elects leader
- Person A: Complete election + heartbeat
- Person B: Cluster config + testing scripts
- **Together**: 
  - Start 3 nodes
  - Watch leader election happen
  - Verify heartbeats prevent new elections
  - Kill leader and watch re-election

---

## Testing Responsibilities

### Person A Tests
- Unit tests for election logic
- Unit tests for state transitions
- Unit tests for term management
- Mock network layer for testing

### Person B Tests
- Unit tests for network server
- Unit tests for network client
- Integration tests for message passing
- End-to-end cluster tests

### Shared Tests (Together)
- Full 3-node cluster test
- Leader failure and re-election test
- Network partition simulation

---

## Detailed Task Breakdown

### Person A - Day 1 Tasks (4-6 hours)
```java
// 1. Add fields to RaftNode.java
private long currentTerm = 0;
private String votedFor = null;
private Timer electionTimer;
private Random random = new Random();

// 2. Implement resetElectionTimer()
// 3. Implement startElection()
// 4. Test with: mvn test
```

### Person B - Day 1 Tasks (4-6 hours)
```java
// 1. Create NetworkServer.java
// 2. Setup Netty ServerBootstrap
// 3. Create channel initializer
// 4. Test with: mvn test
```

---

## Week 1 Deliverables

### Person A Delivers
- ✅ Working election logic
- ✅ Working heartbeat mechanism
- ✅ Term management
- ✅ Unit tests (>80% coverage)
- ✅ Documentation of Raft logic

### Person B Delivers
- ✅ Working network server
- ✅ Working network client
- ✅ Cluster configuration system
- ✅ Docker Compose setup
- ✅ Testing scripts
- ✅ Integration tests

### Together Deliver
- ✅ 3-node cluster demo
- ✅ All code merged to main
- ✅ End of week demo video/screenshots
- ✅ Updated README with setup instructions

---

## Quick Reference

### Person A Priority
1. Election timer
2. Vote request/response
3. Heartbeat sending
4. Heartbeat receiving

### Person B Priority
1. Network server
2. Network client
3. Message serialization
4. Cluster configuration

### Integration Points
- Day 2 evening: Merge and test message sending
- Day 4 evening: Merge and test heartbeats
- Day 7: Full cluster test

---

## Success Metrics

By end of Week 1, you should be able to:
1. Start 3 nodes from command line
2. See logs showing election happening
3. See one node become leader
4. See leader sending heartbeats
5. Kill leader and see new election
6. All tests passing (`mvn test`)

---

## Pro Tips

### For Person A
- Test election logic with mocked network first
- Use `System.currentTimeMillis()` to debug timing
- Log every state transition
- Don't worry about network issues initially

### For Person B
- Start with simple echo server
- Test with `telnet` before integrating
- Use Netty examples as reference
- Make network failures visible in logs

### For Both
- **Commit every hour** - small commits are good
- **Write tests first** when possible
- **Document as you go** - add comments
- **Ask questions early** - don't waste time stuck
- **Celebrate small wins** - working code is progress!

---

## Emergency Contact

If one person is blocked and the other can help:
- **Person A blocked on network?** → Person B writes a simple test harness
- **Person B blocked on Raft logic?** → Person A explains with diagrams
- **Both blocked?** → Take a break, review Raft paper together

---

## Next Steps (Right Now)

### Setup Meeting (30 min - Together)
1. Both clone the repo
2. Both run `mvn clean install`
3. Both create your branches
4. Assign who is Person A and who is Person B
5. Schedule daily standup time
6. Exchange contact info

### First Solo Work (2 hours - Separately)
- Person A: Read WEEK1_GUIDE.md Day 1-2, start election timer
- Person B: Research Netty examples, start NetworkServer.java

### First Integration (Tomorrow Evening)
- Meet and test that both components compile together
- Discuss any interface changes needed

---

Let's go! 🚀 Start with the setup meeting, then dive into your individual tasks!
