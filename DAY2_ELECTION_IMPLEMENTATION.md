# Day 2: Leader Election Implementation

**Person A’s Feature Complete** ✅  
**Date:** Day 2 (Week 1)  
**Implemented by:** Person A  
**Status:** Ready for Day 4 Integration with Heartbeat Code

---

## 📋 Executive Summary

This document describes the full implementation of the **Leader Election** mechanism for our Raft consensus algorithm.  
This was Person A’s primary responsibility and is now complete and verified to work with Person B’s heartbeat and failure detection logic.

### What Was Implemented

1. ✅ **Election Timeout Trigger** – Detect leader failure and start election
2. ✅ **Candidate Transition** – Follower → Candidate on timeout
3. ✅ **Vote Requests (RequestVote RPC)** – Send and receive vote messages
4. ✅ **Vote Responses (RequestVoteResponse RPC)** – Tally votes and decide election result
5. ✅ **Leader Transition** – Candidate → Leader on majority vote
6. ✅ **Follower Reversion** – Candidate/Leader → Follower on higher term discovery
7. ✅ **Statistics Tracking** – Election counts and vote statistics
8. ✅ **Comprehensive Testing** – 12 integration tests verifying full cluster behavior

### Files Created/Modified

src/main/java/com/distributed/cache/
├── raft/
│ └── RaftNode.java (MODIFIED – ~620 lines, +450 lines added for election)
└── network/
├── NetworkBase.java (CREATED Day 1 by Person B – foundation for RPC)
└── MessageSerializer.java (CREATED Day 1)

src/test/java/com/distributed/cache/
├── raft/
│ └── RaftNodeElectionTest.java (CREATED – 12 integration tests)
└── network/
└── NetworkBaseTest.java (Day 1 tests by Person B)

---

## 🎯 Integration Points with Person B

### What Person B Needs to Know

Your election code is now fully integrated with the heartbeat and failure detection logic implemented by Person B. The two mechanisms form a complete Raft consensus loop.

### 1. onElectionTimeout() Integration

**Person B’s original hook** (now implemented by Person A):

```java
private void onElectionTimeout() {
    if (state == RaftState.FOLLOWER || state == RaftState.CANDIDATE) {
        logger.info("Node {} detected leader failure, starting election", nodeId);
        startElection();  // Person A’s implementation
    }
}
```

2. Message Handler Registration

Election RPCs are registered alongside heartbeat handlers:

```java
networkBase.registerMessageHandler(Message.MessageType.REQUEST_VOTE, this::handleRequestVote);
networkBase.registerMessageHandler(Message.MessageType.REQUEST_VOTE_RESPONSE, this::handleRequestVoteResponse);
```

3. Candidate Transition Implementation

```java
private synchronized void becomeCandidate() {
    if (state == RaftState.CANDIDATE) return;
    logger.info("Node {} transitioning to CANDIDATE (term={})", nodeId, currentTerm);
    state = RaftState.CANDIDATE;
    resetElectionTimer();  // for split vote retry
}
```

4. Full Election Flow Added by Person A
   ┌───────────────────────────────┐
   │ ELECTION WORKFLOW (Week 1) │
   └───────────────────────────────┘
1. Follower times out (no heartbeat)
1. → becomeCandidate()
1. → Increment term & vote for self
1. → Send RequestVote to all peers
1. Peers handle RequestVote:
    - Grant if not voted yet and term valid
1. Votes counted in handleRequestVoteResponse()
1. If majority → becomeLeader()
1. Leader starts heartbeat timer (Person B)
1. Followers reset election timer on AppendEntries

🏗️ Architecture Overview
Class Structure Highlights (After Integration)
RaftNode.java
├─ Lifecycle Methods (start, shutdown)
├─ Heartbeat Timer (Person B)
├─ Election Timer (Person B)
├─ Election Logic (Person A)
│ ├─ startElection()
│ ├─ sendRequestVote()
│ ├─ handleRequestVote()
│ ├─ handleRequestVoteResponse()
│ └─ checkElectionResult()
├─ State Transitions
│ ├─ becomeFollower()
│ ├─ becomeCandidate()
│ └─ becomeLeader()
└─ Metrics & Getters

Key timers:

Heartbeat Interval = 50 ms (leaders)

Election Timeout = 150–300 ms (followers)

Startup grace ≈ 5 s to avoid false elections

🔍 Detailed Implementation

1.  startElection()

```java
    private synchronized void startElection() {
    currentTerm++;
    electionsStarted++;

        becomeCandidate();
        votedFor = nodeId;
        votesReceived.clear();
        votesReceived.put(nodeId, true);

        logger.info("Node {} started election for term {} (election #{})", nodeId, currentTerm, electionsStarted);

        resetElectionTimer();
        sendRequestVoteToAll();
        checkElectionResult();

    }
```

Behavior:

Increments term once per election

Transitions to candidate and votes for self

Broadcasts RequestVote to all peers

Uses a fresh election timer for split vote retry

2. RequestVote RPC Handlers
   handleRequestVote()

```java
    private synchronized void handleRequestVote(Message request) {
    long requestTerm = request.getTerm();
    String candidateId = request.getCandidateId();

    Message response = new Message(Message.MessageType.REQUEST_VOTE_RESPONSE, currentTerm, nodeId);
    response.setSuccess(false);

    if (requestTerm > currentTerm) {
        currentTerm = requestTerm;
        becomeFollower();
        votedFor = null;
    }

    if (requestTerm == currentTerm &&
        (votedFor == null || votedFor.equals(candidateId))) {
        response.setSuccess(true);
        votedFor = candidateId;
        resetElectionTimer();
        logger.info("Node {} granted vote to {}", nodeId, candidateId);
    }

    networkBase.sendMessage(request.getSenderId(), response);
}
```

Implements Raft rules:

Reject stale terms

Grant first vote per term

Reset election timer on grant

Send vote response to candidate

handleRequestVoteResponse()

```java
private synchronized void handleRequestVoteResponse(Message response) {
    if (state != RaftState.CANDIDATE) return;

    long responseTerm = response.getTerm();
    if (responseTerm > currentTerm) {
        currentTerm = responseTerm;
        becomeFollower();
        votedFor = null;
        return;
    }

    if (response.isSuccess() && responseTerm == currentTerm) {
        votesReceived.put(response.getSenderId(), true);
        checkElectionResult();
    }
}
```

Counts votes and transitions to leader on majority.

checkElectionResult()

```java
private synchronized void checkElectionResult() {
    if (state != RaftState.CANDIDATE) return;

    int votes = votesReceived.size();
    if (votes >= getMajority()) {
        logger.info("Node {} won election with {}/{} votes", nodeId, votes, getTotalNodes());
        becomeLeader();
    }
}
```

3. Leader Transition

On winning an election:

```java
public synchronized void becomeLeader() {
    if (state == RaftState.LEADER) return;

    state = RaftState.LEADER;
    stopElectionTimer();
    startHeartbeatTimer();  // Person B’s mechanism
    logger.info("Node {} is now leader (term={})", nodeId, currentTerm);
}
```

Immediately starts heartbeats to followers.

📊 Testing Results
Test Suite – RaftNodeElectionTest.java

12 Integration Tests Covering Full Flow
| # | Test Case | Purpose | Status |
| -- | ------------------------------------ | --------------------- | ------ |
| 1 | Nodes Start As Followers | Initial state check | ✅ |
| 2 | Election Timeout Triggers Election | Timeout detection | ✅ |
| 3 | Election Produces One Leader | Leader uniqueness | ✅ |
| 4 | Leader Sends Heartbeats | Leader–follower sync | ✅ |
| 5 | Heartbeats Prevent Elections | Failure prevention | ✅ |
| 6 | Node Votes for First Candidate | Vote grant logic | ✅ |
| 7 | Winner Has Majority Votes | Quorum confirmation | ✅ |
| 8 | All Nodes Converge to Same Term | Term sync | ✅ |
| 9 | System Remains Stable After Election | Steady state | ✅ |
| 10 | Heartbeat Frequency Correct | Timing validation | ✅ |
| 11 | Statistics Tracked Correctly | Telemetry | ✅ |
| 12 | Multiple Elections Can Occur | Re-election stability | ✅ |

All tests pass after synchronization and timing fixes for connection setup.

🧩 Design Rationale
Why Randomized Timeouts?

To avoid split votes: each node times out at a different moment.
Ensures one candidate starts before the others.

Why Majority Rule?

Raft guarantees that at most one leader can exist per term if each term has a unique quorum of voters.

Why Grace Period After Startup?

Prevents false elections while Netty connections stabilize (~500 ms).
Without it, nodes might declare elections before peers are connected.

| Component               | Thread             | Responsibility               |
| ----------------------- | ------------------ | ---------------------------- |
| HeartbeatTimer-{nodeId} | Scheduled Executor | Leader sends heartbeats      |
| ElectionTimer-{nodeId}  | Scheduled Executor | Follower/Candidate timeouts  |
| Netty Boss              | I/O Thread         | Accept connections           |
| Netty Worker            | I/O Thread         | Message handling             |
| Main Thread             | Start-up           | Server init and peer connect |

All critical state mutations (state, term, votedFor) are volatile or inside synchronized methods.

🧠 Key Learnings

Synchronization is critical – Concurrent timers can overlap without locks.

Network jitter affects tests – Election and heartbeat timers must allow buffers.

Randomized timeouts prevent split votes – core to Raft safety.

Quorum logic must be term-aware – reject old terms to avoid stale leaders.

✅ Pre-Integration Checklist

startElection() implemented

Vote granting and responses functional

Follower reversion on higher term

Leader starts heartbeats immediately

Majority rule verified

12/12 tests passing

Code thread-safe and well-commented

🎉 Summary

What Person A Delivered:

✅ Full Raft Leader Election cycle (start → votes → leader → heartbeat)

✅ Synchronized state management for term and votes

✅ Integration with Person B’s heartbeat system

✅ Deterministic tests covering entire cluster lifecycle

✅ Code ready for Week 1 demo and Day 4 joint testing

Outcome: Stable 3-node Raft cluster with automatic leader election and heartbeat maintenance.

Questions or issues? Refer to inline comments in RaftNode.java or contact Person A.
