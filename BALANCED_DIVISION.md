# Balanced Team Division - Both Learn Everything

## Philosophy: Feature-Based Split (Not Layer-Based)

Instead of dividing by "infrastructure vs algorithm", divide by **complete features** where each person implements the full stack for their feature.

**Result**: Both of you understand networking AND consensus!

---

## The New Division Strategy

### Person A: Leader Election System
**Owns**: Everything needed for nodes to elect a leader

### Person B: Heartbeat & Failure Detection System  
**Owns**: Everything needed to maintain and detect leader

**Both**: Work on different features that each require networking + Raft logic!

---

## Week 1: Feature-Based Division

```
┌──────────────────────────────────────────────────────────────────┐
│                    FEATURE OWNERSHIP                              │
└──────────────────────────────────────────────────────────────────┘

Person A: Leader Election Feature (Full Stack)
═══════════════════════════════════════════════════════════════
├─ Network: Vote request/response messaging
├─ Raft: Election timer, vote counting, become leader
├─ State: Candidate state management
└─ Testing: Election tests

Person B: Heartbeat & Monitoring Feature (Full Stack)
═══════════════════════════════════════════════════════════════
├─ Network: Heartbeat (AppendEntries) messaging
├─ Raft: Heartbeat timer, failure detection
├─ State: Leader/Follower state maintenance
└─ Testing: Failure detection tests
```

---

## Detailed Week 1 Breakdown

### Day 1: Setup & Foundations (Together - 4 hours)

**Morning (Together - 2 hours)**
```
Both do this together:
├─ Read Raft paper Sections 1, 3, 5 together
├─ Watch Raft visualization together
├─ Discuss: What are the key components?
├─ Design: Draw message flow diagrams together
└─ Decide: Confirm who owns which feature
```

**Afternoon (Together - 2 hours)**
```
Both work together on shared foundation:
├─ Create NetworkBase.java (both contribute)
│  └─ Common Netty setup code
├─ Create MessageSerializer.java (both contribute)
│  └─ JSON serialization logic
├─ Enhance Message.java (both contribute)
│  └─ Add all message types needed
└─ Test: Simple echo server/client works
```

**Deliverable**: Basic network communication works

---

### Day 2-3: Core Features (Parallel - 12 hours each)

#### Person A: Leader Election Implementation

**Network Layer (4 hours)**
```
Create:
├─ VoteRequestHandler.java
│  └─ Receives and processes vote requests
├─ VoteResponseHandler.java  
│  └─ Receives and processes vote responses
└─ Add to NetworkBase:
   └─ Register vote request/response handlers

Tasks:
[ ] Send RequestVote RPC to all nodes
[ ] Handle incoming RequestVote RPC
[ ] Send RequestVoteResponse back
[ ] Handle incoming RequestVoteResponse
[ ] Test: Two nodes can exchange vote messages
```

**Raft Logic (5 hours)**
```
In RaftNode.java, implement:
├─ Election timer (random 150-300ms)
├─ startElection() method
│  ├─ Increment currentTerm
│  ├─ Vote for self
│  ├─ Transition to CANDIDATE
│  └─ Send RequestVote to all peers
├─ handleRequestVote() method
│  ├─ Check term
│  ├─ Check votedFor
│  ├─ Grant or deny vote
│  └─ Send response
├─ handleRequestVoteResponse() method
│  ├─ Count votes
│  ├─ Check for majority
│  └─ Become LEADER if majority
└─ Tests for all election logic

Tasks:
[ ] Election timer fires correctly
[ ] Node transitions to CANDIDATE
[ ] Vote requests sent to all peers
[ ] Votes are counted correctly
[ ] Becomes LEADER with majority
[ ] Test: Single node becomes leader (mock network)
```

**Integration (3 hours)**
```
[ ] Connect network layer to Raft logic
[ ] Test with 2 real nodes
[ ] Test with 3 real nodes
[ ] Handle edge cases (simultaneous elections)
[ ] Documentation
```

---

#### Person B: Heartbeat & Failure Detection

**Network Layer (4 hours)**
```
Create:
├─ HeartbeatHandler.java (AppendEntries handler)
│  └─ Receives and processes heartbeats
├─ HeartbeatResponseHandler.java
│  └─ Receives and processes heartbeat responses
└─ Add to NetworkBase:
   └─ Register heartbeat handlers

Tasks:
[ ] Send AppendEntries RPC (empty = heartbeat)
[ ] Handle incoming AppendEntries RPC
[ ] Send AppendEntries response back
[ ] Handle incoming response
[ ] Test: Two nodes can exchange heartbeats
```

**Raft Logic (5 hours)**
```
In RaftNode.java, implement:
├─ Heartbeat timer (50ms for leaders)
├─ sendHeartbeats() method
│  ├─ Only leaders send heartbeats
│  ├─ Send to all followers
│  └─ Include current term
├─ handleAppendEntries() method (follower side)
│  ├─ Check term
│  ├─ Accept if term >= currentTerm
│  ├─ Reset election timer (key!)
│  ├─ Stay/become FOLLOWER
│  └─ Send response
├─ Failure detection logic
│  ├─ Follower detects missing heartbeats
│  ├─ Triggers new election
│  └─ Leader steps down if higher term seen
└─ Tests for all heartbeat logic

Tasks:
[ ] Leader sends heartbeats every 50ms
[ ] Followers receive heartbeats
[ ] Followers reset election timer on heartbeat
[ ] Follower becomes CANDIDATE if no heartbeat
[ ] Leader steps down if sees higher term
[ ] Test: Heartbeat prevents election (mock network)
```

**Integration (3 hours)**
```
[ ] Connect network layer to Raft logic
[ ] Test leader sending heartbeats to followers
[ ] Test followers responding to heartbeats
[ ] Test failure detection when leader dies
[ ] Documentation
```

---

### Day 4: Integration Day (Together - 8 hours)

**Morning: Merge & Fix (4 hours)**
```
Together:
├─ Merge Person A's election code
├─ Merge Person B's heartbeat code
├─ Resolve conflicts in RaftNode.java
├─ Ensure both features work together
└─ Fix integration bugs

Critical Integration Points:
├─ Election timer vs Heartbeat timer coordination
├─ State transitions (FOLLOWER → CANDIDATE → LEADER)
├─ What happens when CANDIDATE receives heartbeat?
└─ What happens when LEADER receives vote request?
```

**Afternoon: Integration Testing (4 hours)**
```
Together write tests:
[ ] Test 1: Start 3 nodes, one becomes leader
[ ] Test 2: Leader sends heartbeats, no new elections
[ ] Test 3: Kill leader, new election happens
[ ] Test 4: Network partition scenarios
[ ] Test 5: Simultaneous candidate scenarios

Debug together:
├─ Use logs to trace message flow
├─ Visualize state transitions
└─ Fix timing issues
```

---

### Day 5: Advanced Features (Parallel - 8 hours each)

#### Person A: Term Management & Safety

```
Implement (Full Stack):
├─ Persistent term storage
│  └─ Save currentTerm to disk
│  └─ Load on restart
├─ Term conflict resolution
│  └─ Step down when higher term discovered
│  └─ Update term on any message with higher term
├─ Vote safety rules
│  └─ Only one vote per term
│  └─ Don't vote if already have a leader
├─ Network layer updates
│  └─ Include term in all messages
│  └─ Validate term on receipt
└─ Tests for term management

Learn:
├─ Why terms are "logical clocks"
├─ How terms prevent split brain
└─ Role of persistence in safety
```

#### Person B: Cluster Configuration & Discovery

```
Implement (Full Stack):
├─ ClusterConfig.java
│  └─ Load cluster topology from YAML
├─ PeerManager.java
│  └─ Track other nodes (id, host, port)
│  └─ Maintain connections to all peers
├─ Node discovery
│  └─ Register with peers on startup
│  └─ Handle peer additions/removals
├─ Network layer updates
│  └─ Connection pooling
│  └─ Retry failed connections
└─ Tests for cluster management

Learn:
├─ How nodes discover each other
├─ Connection management in distributed systems
└─ Handling network failures gracefully
```

---

### Day 6: Testing & Refinement (Parallel - 8 hours each)

#### Person A: Election Edge Cases

```
Implement & Test:
├─ Split vote scenarios
│  └─ What if no one gets majority?
│  └─ Re-election after timeout
├─ Old leader returns
│  └─ Leader with old term tries to lead
│  └─ Gets rejected, steps down
├─ Concurrent elections
│  └─ Multiple nodes become candidates
│  └─ Eventually one wins
├─ Network delay scenarios
│  └─ Late vote responses
│  └─ Vote responses after election complete
└─ Write comprehensive tests

Learn:
├─ Why randomized timeouts work
├─ Probability of split votes
└─ Correctness proofs
```

#### Person B: Failure & Recovery Scenarios

```
Implement & Test:
├─ Leader failure detection
│  └─ Timeout-based detection
│  └─ Quick re-election (< 1 second)
├─ Follower failure scenarios
│  └─ Leader detects follower down
│  └─ Leader continues with majority
├─ Network partition scenarios
│  └─ Split brain prevention
│  └─ Minority partition can't elect leader
├─ Recovery after partition heals
│  └─ Node with old state catches up
│  └─ Conflicting leaders resolve
└─ Write comprehensive tests

Learn:
├─ How Raft handles failures
├─ CAP theorem in practice
└─ Network partition trade-offs
```

---

### Day 7: Final Integration & Demo (Together - 8 hours)

**Morning: Final Testing (4 hours)**
```
Together:
├─ Merge all code
├─ Run full test suite
├─ Fix any remaining bugs
├─ Performance testing
│  └─ How fast is election?
│  └─ How much network traffic?
└─ Documentation updates
```

**Afternoon: Demo Preparation (4 hours)**
```
Together:
├─ Create demo scripts
│  └─ Start 3-node cluster
│  └─ Show leader election
│  └─ Kill leader, show re-election
│  └─ Show heartbeat logs
├─ Record demo video
├─ Create architecture diagrams
├─ Write blog post together
└─ Prepare presentation
```

---

## Why This Division Works Better

### ✅ Both Learn Everything

**Person A learns**:
- Network programming (vote messaging)
- Raft consensus (election algorithm)
- Distributed systems debugging
- Term management and safety

**Person B learns**:
- Network programming (heartbeat messaging)
- Raft consensus (failure detection)
- Distributed systems debugging
- Cluster configuration

### ✅ Natural Boundaries

- Election and heartbeat are logically separate
- Minimal code conflicts
- Clear ownership
- Easy to test independently

### ✅ Forced Collaboration

- Day 1: Work together on foundation
- Day 4: Integration day (work together)
- Day 7: Final integration (work together)

### ✅ Equal Complexity

Both features require:
- Understanding Raft paper
- Network programming
- State management
- Testing edge cases

---

## Integration Points (Critical!)

### Shared Code (Both Edit)

**RaftNode.java**: Both will modify this file
```java
// Person A adds:
private void startElection() { ... }
private void handleRequestVote() { ... }

// Person B adds:
private void sendHeartbeats() { ... }
private void handleAppendEntries() { ... }

// COORDINATION NEEDED!
// Use different methods, clear boundaries
// Discuss before editing shared state
```

**Message.java**: Both extend this
```java
// Person A adds:
- RequestVote message type
- RequestVoteResponse message type

// Person B adds:
- AppendEntries message type (heartbeat)
- AppendEntriesResponse message type
```

### Coordination Strategy

**Before editing shared files**:
1. Post in Slack: "Going to add startElection() to RaftNode"
2. Other person responds: "OK, I'll wait to edit RaftNode"
3. Edit, commit, push
4. Notify: "RaftNode updated, your turn"

**Or use VS Code Live Share**:
- Both can see each other's code in real-time
- Prevents conflicts
- Can discuss while coding

---

## Daily Workflow

### Morning Standup (15 min) - 9:00 AM
```
Person A:
├─ Yesterday: Implemented election timer
├─ Today: Working on vote counting logic
└─ Blockers: None

Person B:
├─ Yesterday: Implemented heartbeat sender
├─ Today: Working on failure detection
└─ Blockers: Need to understand term handling

Together Discuss:
├─ Any shared code conflicts?
├─ Any design decisions needed?
└─ When's our next pair programming session?
```

### Solo Work Time
```
Morning:   9:15 AM - 12:00 PM (solo coding)
Afternoon: 1:00 PM - 5:00 PM (solo coding)
```

### Pair Programming (1 hour) - 3:00 PM
```
Monday: Both work on NetworkBase together
Tuesday: Person A explains election, Person B asks questions
Wednesday: Person B explains heartbeat, Person A asks questions  
Thursday: Integration day (all day together)
Friday: Debug together
```

### Evening Sync (30 min) - 6:00 PM
```
├─ Code review each other's PRs
├─ Discuss learnings from the day
├─ Plan tomorrow's work
└─ Update task tracking
```

---

## Learning Through Features

### Person A Learns:

**From Election Feature**:
- Why consensus is hard (split votes, timing)
- Role of randomized timeouts
- Term management
- Majority quorum logic
- RPC request/response patterns

**From Person B** (by reviewing their code):
- How heartbeats prevent elections
- Failure detection mechanisms
- Leader responsibilities
- Follower state management

### Person B Learns:

**From Heartbeat Feature**:
- Leader responsibilities  
- Failure detection trade-offs
- Timer management
- State transitions
- Network reliability patterns

**From Person A** (by reviewing their code):
- Election algorithm details
- Vote counting and majority
- Candidate state management
- Term comparison logic

---

## Week 1 Deliverables

### Person A Delivers:
```
Feature: Leader Election
├─ VoteRequestHandler.java
├─ VoteResponseHandler.java
├─ Election logic in RaftNode.java
│  ├─ Election timer
│  ├─ startElection()
│  ├─ handleRequestVote()
│  ├─ handleRequestVoteResponse()
│  └─ becomeLeader()
├─ Term management
├─ Tests (10+ test cases)
└─ Documentation
```

### Person B Delivers:
```
Feature: Heartbeat & Failure Detection
├─ HeartbeatHandler.java
├─ HeartbeatResponseHandler.java
├─ Heartbeat logic in RaftNode.java
│  ├─ Heartbeat timer
│  ├─ sendHeartbeats()
│  ├─ handleAppendEntries()
│  ├─ resetElectionTimer()
│  └─ detectLeaderFailure()
├─ Cluster configuration
├─ Tests (10+ test cases)
└─ Documentation
```

### Together Deliver:
```
Complete System:
├─ 3-node cluster that elects leader
├─ Leader maintains leadership via heartbeats
├─ Leader failure triggers re-election
├─ All tests passing (40+ total tests)
├─ Integration tests
├─ Demo video
└─ Architecture documentation
```

---

## Git Workflow

### Branch Strategy
```
main (protected)
├── feature/election-system (Person A)
│   ├─ Day 2: Election timer
│   ├─ Day 3: Vote handling
│   ├─ Day 5: Term management
│   └─ [Merge Day 4, 7]
│
└── feature/heartbeat-system (Person B)
    ├─ Day 2: Heartbeat sender
    ├─ Day 3: Failure detection
    ├─ Day 5: Cluster config
    └─ [Merge Day 4, 7]
```

### Merge Schedule
```
Day 1: Both commit to main (foundation code)
Day 4: Integration day - merge both features
Day 7: Final merge - complete system
```

---

## Communication Rules

### High-Bandwidth (Video Call)
- Day 1: All day (foundation)
- Day 4: All day (integration)
- Day 7: All day (final integration)
- Daily: 3:00 PM pair programming (1 hour)

### Quick Messages (Slack/Text)
- Before editing RaftNode.java
- When pushing major changes
- When stuck > 30 minutes
- When completing a milestone

### Asynchronous (PR Comments)
- Code reviews (detailed)
- Design suggestions
- Questions about approach

---

## Testing Strategy

### Person A Tests
```
Unit Tests:
[ ] Election timer fires correctly
[ ] Vote requests contain correct data
[ ] Votes counted correctly
[ ] Becomes leader with majority
[ ] Handles split votes
[ ] Rejects votes from old terms

Integration Tests:
[ ] 2 nodes can complete election
[ ] 3 nodes elect one leader
[ ] Concurrent elections eventually resolve
```

### Person B Tests
```
Unit Tests:
[ ] Heartbeat timer fires correctly
[ ] Heartbeats sent to all followers
[ ] Followers reset timer on heartbeat
[ ] Election triggered without heartbeat
[ ] Leader steps down on higher term

Integration Tests:
[ ] Leader maintains leadership
[ ] Followers stay as followers
[ ] Leader failure detected in < 500ms
[ ] New leader elected after failure
```

### Together Tests (Day 4, 7)
```
System Tests:
[ ] Complete election + heartbeat cycle
[ ] Kill leader, observe re-election
[ ] Network partition scenarios
[ ] 5-node cluster (scale test)
[ ] Performance: election time < 500ms
```

---

## End of Week 1 - What Both Know

By Day 7, both of you can:

### Explain
- [ ] How Raft leader election works
- [ ] Why randomized timeouts prevent split votes
- [ ] How heartbeats maintain leader authority
- [ ] How failure detection works
- [ ] Role of terms in preventing conflicts
- [ ] How to handle network partitions

### Implement
- [ ] Network communication with Netty
- [ ] RPC request/response patterns
- [ ] Timer-based event systems
- [ ] State machine logic
- [ ] Distributed system testing
- [ ] Integration of multiple components

### Debug
- [ ] Read Raft logs to understand system state
- [ ] Trace messages through network layer
- [ ] Identify timing issues
- [ ] Find and fix race conditions
- [ ] Use integration tests to verify correctness

---

## Week 2 Preview

Now that both understand networking + consensus:

### Person A: Log Replication
- AppendEntries with actual log entries
- Log consistency checks
- Commit index management

### Person B: Cache Integration
- PUT/GET/DELETE operations
- Apply committed entries to cache
- Client request handling

**Both features again require full stack!**

---

## Success Metrics

### Technical
- [ ] All tests pass (`mvn test`)
- [ ] 3 nodes elect leader in < 500ms
- [ ] Leader sends heartbeats every 50ms
- [ ] Re-election after leader failure < 1s
- [ ] Zero split-brain scenarios
- [ ] Code coverage > 80%

### Learning
- [ ] Both can explain Raft to someone else
- [ ] Both can implement either feature
- [ ] Both understand network + consensus
- [ ] Both can debug system issues
- [ ] Both can extend the system

### Collaboration
- [ ] Clean git history
- [ ] Well-documented code
- [ ] Good PR reviews
- [ ] Effective pair programming
- [ ] Successful integration days

---

## Start Tomorrow

### Day 1 Morning (Together - 2 hours)
```
9:00 - 10:00  Read Raft paper Sections 1, 3, 5 together
              Take turns explaining paragraphs to each other
              
10:00 - 11:00 Watch Raft visualization
              Discuss: What are the key messages?
              Draw: Message flow diagrams together
              
11:00 - 11:30 Coffee break + informal discussion
              Talk about: What seems hardest?
              
11:30 - 12:00 Setup foundation code together
              Create: NetworkBase.java (pair program)
```

### Day 1 Afternoon (Together - 2 hours)
```
1:00 - 2:00   Finish foundation code
              Create: MessageSerializer.java
              Test: Echo server/client
              
2:00 - 3:00   Plan your features
              Person A: Sketch election flow
              Person B: Sketch heartbeat flow
              Discuss: Integration points
              
3:00          Split up, start solo work!
```

---

## The Bottom Line

**Old way**: Person A does infra, Person B does Raft
- ❌ Person A doesn't learn consensus
- ❌ Person B doesn't learn networking
- ❌ Harder to integrate
- ❌ Imbalanced learning

**New way**: Both do complete features
- ✅ Both learn networking
- ✅ Both learn consensus  
- ✅ Natural integration points
- ✅ Equal learning
- ✅ Better collaboration
- ✅ More fun!

---

**This is how professional teams work on distributed systems!**

Ready to start? Let's build this! 🚀
