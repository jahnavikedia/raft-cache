package com.distributed.cache.raft;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Person A (Election) + Person B (Heartbeat)
 * Tests the complete Raft consensus flow
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RaftNodeElectionTest {
    private static final Logger logger = LoggerFactory.getLogger(RaftNodeElectionTest.class);

    private RaftNode node1;
    private RaftNode node2;
    private RaftNode node3;

    @BeforeEach
    void setUp() throws Exception {
        // use ports distinct and unlikely to conflict on CI
        node1 = new RaftNode("node1", 8001);
        node2 = new RaftNode("node2", 8002);
        node3 = new RaftNode("node3", 8003);

        // Configure peers BEFORE start to ensure they try to dial
        node1.configurePeers(Map.of("node2", "localhost:8002", "node3", "localhost:8003"));
        node2.configurePeers(Map.of("node1", "localhost:8001", "node3", "localhost:8003"));
        node3.configurePeers(Map.of("node1", "localhost:8001", "node2", "localhost:8002"));

        // Start all nodes
        node1.start();
        node2.start();
        node3.start();

        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            int n1 = node1.getConnectedPeerCount();
            int n2 = node2.getConnectedPeerCount();
            int n3 = node3.getConnectedPeerCount();
            if (n1 == 2 && n2 == 2 && n3 == 2)
                break;
            Thread.sleep(300);
        }

        assertEquals(2, node1.getConnectedPeerCount(), "Node1 should be connected to 2 peers");
        assertEquals(2, node2.getConnectedPeerCount(), "Node2 should be connected to 2 peers");
        assertEquals(2, node3.getConnectedPeerCount(), "Node3 should be connected to 2 peers");
    }

    @AfterEach
    void tearDown() {
        if (node1 != null)
            node1.shutdown();
        if (node2 != null)
            node2.shutdown();
        if (node3 != null)
            node3.shutdown();
    }

    // TODO: FIX THIS TEST
    // @Test
    // @Order(1)
    // void testNodesStartAsFollowers() {
    // assertTrue(node1.getState() == RaftState.FOLLOWER || node1.getState() ==
    // RaftState.LEADER);
    // assertTrue(node2.getState() == RaftState.FOLLOWER || node2.getState() ==
    // RaftState.LEADER);
    // assertTrue(node3.getState() == RaftState.FOLLOWER || node3.getState() ==
    // RaftState.LEADER);
    // assertEquals(0, node1.getCurrentTerm());
    // }

    @Test
    @Order(2)
    @DisplayName("Test 2: Election timeout triggers election")
    void testElectionTimeoutTriggersElection() throws InterruptedException {
        // Wait for election timeout (max 300ms + buffer)
        Thread.sleep(500);

        // At least one node should become candidate or leader
        int candidatesOrLeaders = 0;
        if (node1.getState() == RaftState.CANDIDATE || node1.getState() == RaftState.LEADER)
            candidatesOrLeaders++;
        if (node2.getState() == RaftState.CANDIDATE || node2.getState() == RaftState.LEADER)
            candidatesOrLeaders++;
        if (node3.getState() == RaftState.CANDIDATE || node3.getState() == RaftState.LEADER)
            candidatesOrLeaders++;

        assertTrue(candidatesOrLeaders >= 1,
                "At least one node should start election");

        // At least one election should have been started
        long totalElections = node1.getElectionsStarted() +
                node2.getElectionsStarted() +
                node3.getElectionsStarted();
        assertTrue(totalElections >= 1, "At least one election should start");

        logger.info("✓ Election triggered after timeout. Elections started: {}", totalElections);
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Election produces one leader")
    void testElectionProducesLeader() throws InterruptedException {
        // Wait for election to complete
        Thread.sleep(600);

        // Count leaders
        int leaderCount = 0;
        RaftNode leader = null;

        if (node1.getState() == RaftState.LEADER) {
            leaderCount++;
            leader = node1;
        }
        if (node2.getState() == RaftState.LEADER) {
            leaderCount++;
            leader = node2;
        }
        if (node3.getState() == RaftState.LEADER) {
            leaderCount++;
            leader = node3;
        }

        assertEquals(1, leaderCount, "Should have exactly one leader");
        assertNotNull(leader);

        logger.info("✓ Node {} became leader", leader.getNodeId());
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Leader sends heartbeats to followers")
    void testLeaderSendsHeartbeats() throws InterruptedException {
        // Wait for election
        Thread.sleep(600);

        // Find leader
        RaftNode leader = null;
        RaftNode follower1 = null;
        RaftNode follower2 = null;

        if (node1.getState() == RaftState.LEADER) {
            leader = node1;
            follower1 = node2;
            follower2 = node3;
        } else if (node2.getState() == RaftState.LEADER) {
            leader = node2;
            follower1 = node1;
            follower2 = node3;
        } else if (node3.getState() == RaftState.LEADER) {
            leader = node3;
            follower1 = node1;
            follower2 = node2;
        }

        assertNotNull(leader, "Should have a leader");

        // Wait for heartbeats
        Thread.sleep(200);

        // Leader should send heartbeats
        assertTrue(leader.getHeartbeatsSent() > 0,
                "Leader should send heartbeats");

        // Followers should receive heartbeats
        assertTrue(follower1.getHeartbeatsReceived() > 0,
                "Follower 1 should receive heartbeats");
        assertTrue(follower2.getHeartbeatsReceived() > 0,
                "Follower 2 should receive heartbeats");

        logger.info("✓ Leader sent {} heartbeats", leader.getHeartbeatsSent());
        logger.info("✓ Followers received {}/{} heartbeats",
                follower1.getHeartbeatsReceived(),
                follower2.getHeartbeatsReceived());
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Heartbeats prevent unnecessary elections")
    void testHeartbeatsPreventElections() throws InterruptedException {
        // Wait for first election
        Thread.sleep(600);

        // Record election counts
        long elections1 = node1.getElectionsStarted();
        long elections2 = node2.getElectionsStarted();
        long elections3 = node3.getElectionsStarted();

        // Wait longer than election timeout while heartbeats active
        Thread.sleep(500);

        // Election counts should not increase (heartbeats prevent this)
        long newElections1 = node1.getElectionsStarted();
        long newElections2 = node2.getElectionsStarted();
        long newElections3 = node3.getElectionsStarted();

        // At most one more election (if there was a split vote initially)
        long totalNewElections = (newElections1 - elections1) +
                (newElections2 - elections2) +
                (newElections3 - elections3);

        assertTrue(totalNewElections <= 1,
                "Heartbeats should prevent unnecessary elections");

        logger.info("✓ Heartbeats prevented elections. New elections: {}",
                totalNewElections);
    }

    // ========== Vote Granting Tests ==========

    @Test
    @Order(6)
    @DisplayName("Test 6: Node votes for first candidate in term")
    void testVoteForFirstCandidate() throws InterruptedException {
        // Wait for election to start
        Thread.sleep(800);

        // At least one node should have voted (for self or another)
        boolean someoneVoted = node1.getVotedFor() != null ||
                node2.getVotedFor() != null ||
                node3.getVotedFor() != null;

        assertTrue(someoneVoted, "At least one node should vote during election");

        logger.info("✓ Nodes voted: node1={}, node2={}, node3={}",
                node1.getVotedFor(), node2.getVotedFor(), node3.getVotedFor());
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Winner has majority votes")
    void testWinnerHasMajority() throws InterruptedException {
        // Wait for election
        Thread.sleep(600);

        // Find leader
        RaftNode leader = null;
        if (node1.getState() == RaftState.LEADER)
            leader = node1;
        else if (node2.getState() == RaftState.LEADER)
            leader = node2;
        else if (node3.getState() == RaftState.LEADER)
            leader = node3;

        assertNotNull(leader, "Should have a leader");

        // Leader should have received majority (at least 2/3 votes)
        int voteCount = leader.getVoteCount();
        assertTrue(voteCount >= 2,
                "Leader should have majority (got " + voteCount + "/3 votes)");

        logger.info("✓ Leader {} won with {}/3 votes",
                leader.getNodeId(), voteCount);
    }

    // ========== Term Management Tests ==========

    @Test
    @Order(8)
    @DisplayName("Test 8: All nodes converge to same term")
    void testTermConvergence() throws InterruptedException {
        // Wait for election and stabilization
        Thread.sleep(800);

        long term1 = node1.getCurrentTerm();
        long term2 = node2.getCurrentTerm();
        long term3 = node3.getCurrentTerm();

        // All should have same term (or within 1 due to timing)
        long maxTerm = Math.max(term1, Math.max(term2, term3));
        long minTerm = Math.min(term1, Math.min(term2, term3));

        assertTrue(maxTerm - minTerm <= 1,
                "All nodes should converge to similar terms");

        logger.info("✓ Terms converged: node1={}, node2={}, node3={}",
                term1, term2, term3);
    }

    // ========== Stability Tests ==========

    @Test
    @Order(9)
    @DisplayName("Test 9: System remains stable after election")
    void testSystemStability() throws InterruptedException {
        // Wait for election
        Thread.sleep(600);

        RaftNode leader = null;
        if (node1.getState() == RaftState.LEADER)
            leader = node1;
        else if (node2.getState() == RaftState.LEADER)
            leader = node2;
        else if (node3.getState() == RaftState.LEADER)
            leader = node3;

        assertNotNull(leader);
        String leaderId = leader.getNodeId();

        // Wait and verify leader stays leader
        Thread.sleep(500);

        assertEquals(RaftState.LEADER, leader.getState(),
                "Leader should remain leader");

        // Verify followers stay followers
        for (RaftNode node : new RaftNode[] { node1, node2, node3 }) {
            if (!node.getNodeId().equals(leaderId)) {
                assertEquals(RaftState.FOLLOWER, node.getState(),
                        "Followers should remain followers");
            }
        }

        logger.info("✓ System stable: {} is leader, others are followers", leaderId);
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Heartbeat frequency is correct")
    void testHeartbeatFrequency() throws InterruptedException {
        // Wait for election
        Thread.sleep(600);

        // Find leader
        RaftNode leader = null;
        if (node1.getState() == RaftState.LEADER)
            leader = node1;
        else if (node2.getState() == RaftState.LEADER)
            leader = node2;
        else if (node3.getState() == RaftState.LEADER)
            leader = node3;

        assertNotNull(leader);

        // Record heartbeat count
        long heartbeats1 = leader.getHeartbeatsSent();

        // Wait 500ms
        Thread.sleep(500);

        long heartbeats2 = leader.getHeartbeatsSent();
        long heartbeatsInInterval = heartbeats2 - heartbeats1;

        // Should send ~10 heartbeats in 500ms (every 50ms)
        // Allow variance: 8-12 heartbeats
        assertTrue(heartbeatsInInterval >= 8 && heartbeatsInInterval <= 12,
                "Expected 8-12 heartbeats in 500ms, got " + heartbeatsInInterval);

        logger.info("✓ Leader sent {} heartbeats in 500ms (expected ~10)",
                heartbeatsInInterval);
    }

    // ========== Statistics Tests ==========

    @Test
    @Order(11)
    @DisplayName("Test 11: Statistics are tracked correctly")
    void testStatistics() throws InterruptedException {
        // Wait for election
        Thread.sleep(800);

        // At least one election should have started
        long totalElections = node1.getElectionsStarted() +
                node2.getElectionsStarted() +
                node3.getElectionsStarted();
        assertTrue(totalElections >= 1);

        // Find leader
        RaftNode leader = null;
        RaftNode follower = null;

        if (node1.getState() == RaftState.LEADER) {
            leader = node1;
            follower = node2;
        } else if (node2.getState() == RaftState.LEADER) {
            leader = node2;
            follower = node1;
        } else {
            leader = node3;
            follower = node1;
        }

        assertNotNull(leader);
        assertNotNull(follower);

        // Leader should have sent heartbeats
        assertTrue(leader.getHeartbeatsSent() > 0);

        // Follower should have received heartbeats
        assertTrue(follower.getHeartbeatsReceived() > 0);
        assertTrue(follower.getLastHeartbeatReceived() > 0);

        logger.info("✓ Statistics:");
        logger.info("  - Total elections: {}", totalElections);
        logger.info("  - Leader heartbeats sent: {}", leader.getHeartbeatsSent());
        logger.info("  - Follower heartbeats received: {}", follower.getHeartbeatsReceived());
    }

    // ========== Edge Case Tests ==========

    @Test
    @Order(12)
    @DisplayName("Test 12: Multiple elections can occur")
    void testMultipleElections() throws InterruptedException {
        // Wait for first election
        Thread.sleep(600);

        // Manually trigger another election by advancing term on all nodes
        node1.setCurrentTerm(node1.getCurrentTerm() + 1);
        node2.setCurrentTerm(node2.getCurrentTerm() + 1);
        node3.setCurrentTerm(node3.getCurrentTerm() + 1);

        // Wait for new election
        Thread.sleep(600);

        // System should still converge to one leader
        int leaderCount = 0;
        if (node1.getState() == RaftState.LEADER)
            leaderCount++;
        if (node2.getState() == RaftState.LEADER)
            leaderCount++;
        if (node3.getState() == RaftState.LEADER)
            leaderCount++;

        assertEquals(1, leaderCount, "Should still have exactly one leader");

        logger.info("✓ System recovered after forced re-election");
    }
}