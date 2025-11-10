# Week 1 Task Tracking - Quick Reference

## Person A: Raft Logic Developer 🧠

### Day 1-2: Election Logic ⚡
```
Branch: feature/election-logic
Priority: HIGH

Tasks:
[ ] Add election timer fields to RaftNode
[ ] Implement resetElectionTimer()
[ ] Implement startElection()
[ ] Add currentTerm, votedFor fields
[ ] Implement vote request creation
[ ] Test election triggers after timeout

Files: RaftNode.java, RaftNodeTest.java
Time: 8-10 hours
```

### Day 3-4: Vote Handling 🗳️
```
Branch: feature/election-logic

Tasks:
[ ] Implement handleRequestVote()
[ ] Implement handleRequestVoteResponse()
[ ] Add vote counting logic
[ ] Transition to LEADER on majority
[ ] Handle vote rejection cases
[ ] Test with mock network

Files: RaftNode.java, RaftNodeTest.java
Time: 8-10 hours
```

### Day 5-7: Heartbeat & Terms 💓
```
Branch: feature/heartbeat

Tasks:
[ ] Add heartbeat timer for leaders
[ ] Implement sendHeartbeats()
[ ] Implement handleAppendEntries()
[ ] Reset election timer on heartbeat
[ ] Handle term conflicts
[ ] Integration tests

Files: RaftNode.java, HeartbeatTest.java
Time: 10-12 hours
```

**Week 1 Total: ~30 hours**

---

## Person B: Infrastructure Developer 🏗️

### Day 1-2: Network Server 🌐
```
Branch: feature/network-server
Priority: HIGH

Tasks:
[ ] Create NetworkServer.java
[ ] Setup Netty ServerBootstrap
[ ] Create MessageHandler interface
[ ] Create MessageHandlerAdapter
[ ] Add JSON serialization
[ ] Test server accepts connections

Files: NetworkServer.java, MessageHandler.java, 
       MessageHandlerAdapter.java, NetworkServerTest.java
Time: 8-10 hours
```

### Day 3-4: Network Client 📡
```
Branch: feature/network-client

Tasks:
[ ] Create NetworkClient.java
[ ] Setup Netty client Bootstrap
[ ] Implement sendMessage()
[ ] Add connection management
[ ] Handle connection failures
[ ] Test client sends messages

Files: NetworkClient.java, ConnectionManager.java,
       NetworkClientTest.java
Time: 8-10 hours
```

### Day 5-7: Config & Testing 🧪
```
Branch: feature/cluster-config

Tasks:
[ ] Create cluster.yaml config
[ ] Create ClusterConfig.java
[ ] Add node discovery
[ ] Create test-cluster.sh script
[ ] Create docker-compose.yml
[ ] Write integration tests
[ ] Document setup

Files: cluster.yaml, ClusterConfig.java,
       docker-compose.yml, test-cluster.sh
Time: 10-12 hours
```

**Week 1 Total: ~30 hours**

---

## Integration Checkpoints 🤝

### Checkpoint 1: Day 2 Evening (30 min)
```
Goal: Verify components compile together

Person A has: Election timer that triggers
Person B has: Network server that starts

Test Together:
1. Start node with network server
2. Verify no compilation errors
3. Check logs show both components working

Success: Node starts, election timer runs, server listens
```

### Checkpoint 2: Day 4 Evening (1 hour)
```
Goal: Verify message passing works

Person A has: Vote request creation
Person B has: Client can send messages

Test Together:
1. Start two nodes
2. Node 1 sends vote request to Node 2
3. Check logs show message received

Success: Messages flow between nodes
```

### Checkpoint 3: Day 7 Evening (2 hours)
```
Goal: Full 3-node election

Person A has: Complete election + heartbeat
Person B has: Cluster config + scripts

Test Together:
1. Use test-cluster.sh to start 3 nodes
2. Watch logs for election
3. Verify one leader elected
4. Verify heartbeats prevent new elections
5. Kill leader, verify re-election

Success: Leader election and heartbeat working!
```

---

## Daily Standup Template 📋

**Time: 9:00 AM (15 minutes)**

### Person A Reports:
- Yesterday: _____
- Today: _____
- Blockers: _____

### Person B Reports:
- Yesterday: _____
- Today: _____
- Blockers: _____

### Together Decide:
- Any code review needed?
- Any integration needed today?
- Any design decisions to make?

---

## Merge Strategy 🔀

### Option 1: Person A Merges First (Recommended)
```bash
# Day 2 - Person A merges election logic
Person A: git checkout main && git merge feature/election-logic
Person B: git checkout main && git pull && git merge main into feature/network-server

# Day 4 - Person B merges network
Person B: git checkout main && git merge feature/network-client
Person A: git checkout main && git pull && git merge main into feature/heartbeat

# Day 7 - Final merge
Person A: merge feature/heartbeat
Person B: merge feature/cluster-config
```

### Conflict Resolution
If both edited same file (likely RaftNode.java):
```
1. Person with merge conflict pulls latest main
2. Manually merge changes (VS Code has good merge tool)
3. Test that everything compiles: mvn clean install
4. Test that everything works: mvn test
5. Then push
```

---

## Communication Rules 📞

### Must Communicate Before:
- Changing RaftNode.java (both edit this)
- Changing Message.java (both use this)
- Merging to main (coordinate timing)
- Making breaking changes to interfaces

### Quick Messages (Slack/Text):
"Starting work on election timer"
"Need to change Message.java to add field X"
"Blocked on Y, can you help?"
"Pushing feature/election-logic, ready for review"
"Ready to merge, are you?"

### Longer Discussions (Call/Meet):
- Design decisions affecting both sides
- Debugging integration issues
- Planning next week
- Code review discussions

---

## Testing Checklist ✅

### Person A Tests
```
[ ] Election timer fires after timeout
[ ] Vote requests contain correct data
[ ] Vote counting works correctly
[ ] Becomes leader with majority votes
[ ] Heartbeat timer fires periodically
[ ] Followers reset timer on heartbeat
[ ] Higher term causes step down
```

### Person B Tests
```
[ ] Server starts on correct port
[ ] Server accepts connections
[ ] Messages deserialize correctly
[ ] Client connects to server
[ ] Client sends messages successfully
[ ] Connection failures handled gracefully
[ ] Multiple nodes can communicate
```

### Integration Tests (Together)
```
[ ] 2 nodes can exchange messages
[ ] 3 nodes can all communicate
[ ] Election completes successfully
[ ] Leader sends heartbeats to all
[ ] Follower receives heartbeats
[ ] Leader failure triggers re-election
```

---

## Troubleshooting Guide 🔧

### Person A Issues

**Problem**: Election timer not firing
- Check Timer is initialized
- Check timeout calculation
- Add log in timer callback

**Problem**: Votes not counting correctly
- Log each vote received
- Check majority calculation
- Verify votedFor is reset

### Person B Issues

**Problem**: Server won't start
- Check port not in use: `lsof -i :8001`
- Check Netty dependencies
- Check for errors in bootstrap

**Problem**: Messages not sending
- Verify client connects: check connection logs
- Verify serialization: print JSON before sending
- Check network reachability

### Integration Issues

**Problem**: Nodes can't communicate
- Check firewall rules
- Check ports are correct
- Verify both server and client working independently

**Problem**: Compilation errors after merge
- Pull latest main
- Rebuild: `mvn clean install`
- Check for conflicting method signatures

---

## End of Week 1 Demo Script 🎬

```bash
# Terminal 1
./test-cluster.sh

# Wait 10 seconds
# Check logs for:
# - "Starting election" messages
# - "Node X elected as leader"
# - "Sending heartbeat" from leader
# - "Received heartbeat" from followers

# Terminal 2 (kill leader)
kill <leader-pid>

# Check logs for:
# - Followers detect leader failure
# - New election starts
# - New leader elected
# - Heartbeats resume

# Success! 🎉
```

---

## Week 1 Success Criteria 🎯

You are DONE when:
- [x] All tests pass: `mvn test`
- [x] 3 nodes can start independently
- [x] Leader election completes in < 5 seconds
- [x] Leader sends heartbeats every 50ms
- [x] Followers don't start unnecessary elections
- [x] Killing leader triggers new election
- [x] All code merged to main branch
- [x] README updated with run instructions
- [x] Demo video/screenshots captured

---

## Next Week Preview 👀

### Week 2 Division
**Person A**: Log replication (AppendEntries with data)
**Person B**: Cache operations (PUT/GET/DELETE)
**Together**: Integrate cache with Raft log

But don't think about that yet - focus on Week 1! 

---

## Motivation 💪

Week 1 is the HARDEST week because you're building the foundation. After this:
- Week 2 builds on working consensus (easier)
- Week 3 is mostly Python (separate project)
- Week 4 is testing and polish (fun!)

You got this! One task at a time! 🚀

---

**Print this out or keep it open while working!**
