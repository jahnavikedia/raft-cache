# Cross-Learning Guide: Person B Learning Consensus

## Philosophy: Learn While You Build

**Your Role**: Infrastructure Developer (Person B)  
**Your Goal**: Also deeply understand Raft consensus  
**Strategy**: Pair programming + self-study + code review

---

## The Plan: 70-30 Split

### Your Time Allocation
- **70% on your tasks** (Infrastructure) - ~21 hours
- **30% learning consensus** (Raft deep dive) - ~9 hours

This keeps the project on track while you learn both sides.

---

## Week 1: Parallel Learning Strategy

### Day 1-2: Foundation

#### Your Primary Work (6 hours)
- Build NetworkServer.java
- Implement MessageHandler
- Test server startup

#### Your Learning Time (2 hours)
```
Morning (30 min): Read Raft paper Section 1-3
- What problem does Raft solve?
- Leader election overview
- Why consensus matters

Afternoon (30 min): Watch Person A code
- Sit together or screen share
- Ask: "Why are you adding this timer?"
- Ask: "What happens when timeout fires?"

Evening (1 hour): Implement election timer yourself
- Create a TEST branch: feature/learning-raft
- Copy Person A's election timer code
- Modify it, break it, understand it
- NO NEED TO MERGE - just for learning
```

---

### Day 3-4: Deep Dive

#### Your Primary Work (6 hours)
- Build NetworkClient.java
- Implement sendMessage()
- Test message sending

#### Your Learning Time (3 hours)
```
Morning (1 hour): Raft paper Section 5 (Leader Election)
- Read the election algorithm
- Understand RequestVote RPC
- Draw the state machine diagram yourself

Afternoon (1 hour): Pair program with Person A
- Ask to share screen while they code vote handling
- Ask questions: "Why check term first?"
- Discuss edge cases together

Evening (1 hour): Code review + experiments
- Review Person A's PR in detail
- Try to predict what each line does
- On your learning branch, implement vote handling
- Compare your approach vs Person A's
```

---

### Day 5-7: Integration Learning

#### Your Primary Work (8 hours)
- Cluster configuration
- Docker setup
- Integration tests

#### Your Learning Time (4 hours)
```
Day 5 (1.5 hours):
- Raft paper Section 6 (Log Replication)
- Understand AppendEntries RPC
- How does heartbeat prevent elections?

Day 6 (1.5 hours):
- Pair debug with Person A
- Run 3 nodes together
- Watch logs and ask: "Why did Node 2 vote for Node 1?"
- Trace message flow through your network code

Day 7 (1 hour):
- Write a blog post explaining Raft
- Teaching others = best way to learn
- Share with Person A for feedback
```

---

## Specific Learning Activities

### 1. Morning Reading (15-30 min daily)

**Day 1**: Raft paper intro + Section 3  
**Day 2**: Raft paper Section 5.1 (Election basics)  
**Day 3**: Raft paper Section 5.2 (Election details)  
**Day 4**: Raft visualization at raft.github.io  
**Day 5**: Raft paper Section 5.3 (Heartbeat)  
**Day 6**: Re-read entire election section  
**Day 7**: Study Person A's complete code  

### 2. Pair Programming Sessions (30-60 min, 3x per week)

**Tuesday (Day 2)**: Watch Person A implement election timer
```
Questions to ask:
- Why use Random for timeout?
- What if two nodes timeout at same time?
- How does this prevent split votes?
```

**Thursday (Day 4)**: Watch Person A implement vote handling
```
Questions to ask:
- Why check term before voting?
- What's votedFor field for?
- How do we ensure only one vote per term?
```

**Saturday (Day 6)**: Debug together
```
Activities:
- Kill the leader
- Watch re-election in logs
- Trace which node voted for whom
- Understand why new leader was chosen
```

### 3. Hands-On Experiments (1-2 hours, 2x per week)

**Wednesday (Day 3)**: Election Timer Experiment
```bash
# On your learning branch
cd raft-cache
git checkout -b feature/learning-raft

# Modify RaftNode.java
# Change ELECTION_TIMEOUT_MIN to 50 (very short)
# Run node, observe it starts elections rapidly
# Question: Why is 150-300ms the right range?

# Change to 5000 (very long)
# Run node, observe slow election
# Question: Trade-off between fast election and stability?
```

**Friday (Day 5)**: Vote Request Experiment
```bash
# Create test to send vote requests manually
# What happens if term is lower?
# What happens if already voted for someone else?
# What happens if candidate's log is outdated?
```

### 4. Code Review Deep Dives (30 min daily)

**Process**:
1. Person A pushes code
2. You review the PR thoroughly
3. For EVERY method, ask yourself:
   - What is this method's purpose?
   - What would break if we removed this line?
   - Why did they implement it this way?
4. Write comments with questions
5. Person A explains their reasoning

**Example Code Review Questions**:
```java
// Person A's code:
private void startElection() {
    state = RaftState.CANDIDATE;
    currentTerm++;
    votedFor = nodeId;
    // ...
}

// Your review comments:
// Q1: Why increment term BEFORE sending vote requests?
// Q2: Why vote for self? Can't we just request votes?
// Q3: What if we receive a vote request while in CANDIDATE state?
```

---

## Learning Branch Strategy

### Keep a Parallel Learning Branch

```bash
# Your main work
feature/network-server (merge to main)
feature/network-client (merge to main)

# Your learning experiments (NEVER merge)
feature/learning-raft (your playground)
```

**On your learning branch**:
- Implement Raft logic yourself
- Try different approaches
- Break things intentionally
- Compare with Person A's implementation

**Benefits**:
- Hands-on understanding
- No pressure (doesn't need to work perfectly)
- Can experiment freely
- Compare approaches later

---

## Week 1 Learning Deliverables

By end of Week 1, you should be able to:

### Understanding (Mental Model)
- [ ] Explain why distributed consensus is hard
- [ ] Draw Raft state machine (Follower → Candidate → Leader)
- [ ] Explain leader election algorithm step-by-step
- [ ] Explain why randomized timeouts prevent split votes
- [ ] Explain what heartbeats do
- [ ] Explain term numbers and why they matter

### Practical (Hands-On)
- [ ] Trace a vote request through your network code
- [ ] Predict which node will become leader (given timeouts)
- [ ] Debug election failures by reading logs
- [ ] Implement basic election timer yourself (on learning branch)
- [ ] Modify election timeout and observe effects

### Communication (Teaching)
- [ ] Explain Raft to a friend in simple terms
- [ ] Write 500-word blog post on "How Raft Leader Election Works"
- [ ] Draw diagrams of message flow during election
- [ ] Answer Person A's questions about your network code

---

## Effective Pair Programming Tips

### When Watching Person A Code:

**DON'T**:
- Sit silently
- Just nod along
- Interrupt constantly

**DO**:
- Ask "why" questions every 5-10 minutes
- Request to pause and explain complex parts
- Suggest alternatives: "Could we do X instead?"
- Take notes on key concepts
- Offer to be the "driver" for 15 min (you type, they guide)

### Sample Dialogue:
```
You: "Wait, why are we incrementing currentTerm here?"
Person A: "Because we're starting a new election..."
You: "But what if we receive a message with higher term?"
Person A: "Good question! Then we'd step down..."
You: "Can you show me where that happens?"
Person A: [shows code]
You: "Ah, I see! So term is like a logical clock?"
Person A: "Exactly!"
```

---

## Integration Work = Best Learning Opportunity

### Your Integration Tests Teach You Raft

When you write integration tests (Day 5-7), you're actually learning consensus deeply:

**Test 1: Start 3 nodes, verify election**
```java
@Test
void testThreeNodesElectLeader() {
    // Start nodes
    // Wait 500ms
    // Check: Exactly one leader exists
    // Check: Two followers exist
    // Check: Leader has higher term
    
    // YOU NEED TO UNDERSTAND:
    // - How election completes
    // - What defines a leader
    // - How followers recognize leader
}
```

**Test 2: Kill leader, verify re-election**
```java
@Test
void testLeaderFailureTriggersReelection() {
    // Start 3 nodes
    // Wait for leader election
    // Kill leader process
    // Wait 500ms
    // Check: New leader elected
    
    // YOU NEED TO UNDERSTAND:
    // - How followers detect leader failure
    // - What triggers new election
    // - How new leader is chosen
}
```

**Writing these tests FORCES you to understand Raft!**

---

## Daily Schedule with Learning

### Morning (3 hours)
```
9:00 - 9:15   Standup (both)
9:15 - 9:45   Read Raft paper (you alone)
9:45 - 12:00  Your primary work (infrastructure)
```

### Afternoon (4 hours)
```
1:00 - 3:00   Your primary work (infrastructure)
3:00 - 3:30   Break / Process what you learned
3:30 - 5:00   Your primary work (infrastructure)
```

### Evening (2 hours)
```
6:00 - 6:30   Code review Person A's work (learning!)
6:30 - 7:00   Pair programming or sync
7:00 - 7:30   (Optional) Experiments on learning branch
```

**Total**: 9 hours work, with ~2 hours focused learning

---

## Resources for Self-Study

### Must-Read
1. **Raft Paper**: https://raft.github.io/raft.pdf
   - Read Sections 1, 3, 5 carefully
   - Skip Section 7-8 for now

2. **Raft Visualization**: https://raft.github.io/
   - Play with the interactive demo
   - Watch elections happen visually
   - Experiment with failures

### Recommended
3. **Raft PhD Thesis** (optional, very detailed)
4. **MIT 6.824 Lecture on Raft** (YouTube)
5. **"Students Guide to Raft"** (blog post)

### Your Own Code
6. **Person A's Pull Requests** - Best learning resource!
   - Every line teaches you something
   - Compare with paper's pseudocode
   - Ask questions on unclear parts

---

## Week 2 Preview

By Week 2, you'll have strong Raft foundation, so:

### Week 2 Role Switch (Optional)
- **Person A**: Works on cache integration
- **Person B**: You implement log replication!
- Now you can contribute to Raft code

### Why This Works
- Week 1: You learned by watching + studying
- Week 2: You apply by implementing
- Better understanding than just reading

---

## Measuring Your Learning

### End of Week 1 Self-Assessment

Rate yourself 1-5 on each:

**Conceptual Understanding**
- [ ] I can explain why we need consensus (____/5)
- [ ] I understand leader election algorithm (____/5)
- [ ] I understand role of terms (____/5)
- [ ] I understand heartbeat mechanism (____/5)

**Practical Skills**
- [ ] I can read Raft logs and understand them (____/5)
- [ ] I can debug election issues (____/5)
- [ ] I can predict system behavior (____/5)
- [ ] I can implement basic Raft logic (____/5)

**Goal**: Average > 3.5/5 by end of Week 1

If below 3.5, spend more time on learning activities!

---

## The Benefits

### Why Learn Both Sides?

**1. Better Collaboration**
- Understand Person A's challenges
- Suggest better network APIs
- Debug integration issues faster

**2. Resume Impact**
"Implemented distributed cache with Raft consensus (both algorithm and infrastructure)"
vs just
"Built network layer for distributed cache"

**3. Interview Prep**
- System design interviews ask about consensus
- You can speak to both algorithm and implementation
- Shows full-stack distributed systems knowledge

**4. Future Weeks**
- Week 2: Can contribute to log replication
- Week 3: Better understand consistency guarantees
- Week 4: Can explain entire system

---

## Action Plan for Tomorrow

### Setup (15 min)
```bash
# Create your learning branch
git checkout -b feature/learning-raft

# Print out Raft paper Sections 1-5
# Keep it next to your computer
```

### Morning (30 min before work)
```
1. Read Raft paper Section 1-3 (introduction)
2. Watch first 15 min of MIT 6.824 Raft lecture
3. Write down 3 questions about what you don't understand
```

### During Work (ask Person A)
```
- When they start coding, ask them to explain first
- Every hour, take 5 min to review what they implemented
- Before they commit, review the diff together
```

### Evening (30 min after work)
```
1. Re-read the code Person A wrote today
2. On your learning branch, try to implement it yourself
3. Compare your version with theirs
4. Write notes on what you learned
```

---

## Remember

**You're not slowing down the project** by learning!
- Better understanding = better integration
- Asking questions improves Person A's code
- Learning now prevents confusion later

**It's okay to not understand everything immediately**
- Raft is complex (PhD-level research)
- You're learning while building (hardest way)
- Each day you'll understand more

**The goal is progress, not perfection**
- Week 1: Understand basics
- Week 2: Understand deeply  
- Week 3: Can implement independently
- Week 4: Can teach others

---

## Final Tips

1. **Ask "dumb" questions** - they're usually the smartest ones
2. **Draw diagrams** - visual learning is powerful
3. **Explain back** - "So what you're saying is..."
4. **Sleep on it** - understanding comes with time
5. **Enjoy it** - distributed systems are fascinating!

---

**You've got this!** 🎯

By the end of Week 1, you'll understand BOTH the network infrastructure AND the consensus algorithm. That's a powerful combination!

Start tomorrow with reading the Raft paper during breakfast! ☕📖
