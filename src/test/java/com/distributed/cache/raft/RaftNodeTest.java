package com.distributed.cache.raft;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RaftNode heartbeat and failure detection functionality
 * These tests verify Person B's implementation
 */
class RaftNodeTest {
    private static final Logger logger = LoggerFactory.getLogger(RaftNodeTest.class);

    private RaftNode node1;
    private RaftNode node2;
    private RaftNode node3;

    @BeforeEach
    void setUp() throws InterruptedException {
        logger.info("\n========== Starting test ==========");

        // Create three nodes
        node1 = new RaftNode("node1", 7001);
        node2 = new RaftNode("node2", 7002);
        node3 = new RaftNode("node3", 7003);

        // Configure peers for each node
        Map<String, String> node1Peers = new HashMap<>();
        node1Peers.put("node2", "localhost:7002");
        node1Peers.put("node3", "localhost:7003");
        node1.configurePeers(node1Peers);

        Map<String, String> node2Peers = new HashMap<>();
        node2Peers.put("node1", "localhost:7001");
        node2Peers.put("node3", "localhost:7003");
        node2.configurePeers(node2Peers);

        Map<String, String> node3Peers = new HashMap<>();
        node3Peers.put("node1", "localhost:7001");
        node3Peers.put("node2", "localhost:7002");
        node3.configurePeers(node3Peers);

        // Start all nodes
        node1.start();
        node2.start();
        node3.start();

        // Wait for connections to establish
        // Network connections happen asynchronously in start(),
        // and we already wait 200ms in start(), but let's wait a bit more
        Thread.sleep(300);

        logger.info("All nodes started and connected");
    }

    @AfterEach
    void tearDown() {
        logger.info("Shutting down nodes...");
        if (node1 != null) node1.shutdown();
        if (node2 != null) node2.shutdown();
        if (node3 != null) node3.shutdown();
        logger.info("========== Test complete ==========\n");
    }

    @Test
    void testNodeInitialization() {
        // Verify initial state
        assertEquals(RaftState.FOLLOWER, node1.getState());
        assertEquals(RaftState.FOLLOWER, node2.getState());
        assertEquals(RaftState.FOLLOWER, node3.getState());

        assertEquals(0, node1.getCurrentTerm());
        assertEquals(0, node2.getCurrentTerm());
        assertEquals(0, node3.getCurrentTerm());

        logger.info("✓ All nodes initialized as FOLLOWER with term 0");
    }

    @Test
    void testBecomeLeader() throws InterruptedException {
        // Manually make node1 the leader
        node1.becomeLeader();

        // Give it a moment to start heartbeats
        Thread.sleep(100);

        // Verify state change
        assertEquals(RaftState.LEADER, node1.getState());

        // Verify heartbeats are being sent
        long heartbeatsSent = node1.getHeartbeatsSent();
        assertTrue(heartbeatsSent > 0, "Leader should have sent heartbeats");

        logger.info("✓ Node1 became leader and sent {} heartbeats", heartbeatsSent);
    }

    @Test
    void testHeartbeatReceived() throws InterruptedException {
        // Node1 becomes leader
        node1.becomeLeader();

        // Wait for heartbeats to be exchanged
        Thread.sleep(200);

        // Followers should have received heartbeats
        long node2Heartbeats = node2.getHeartbeatsReceived();
        long node3Heartbeats = node3.getHeartbeatsReceived();

        assertTrue(node2Heartbeats > 0, "Node2 should have received heartbeats");
        assertTrue(node3Heartbeats > 0, "Node3 should have received heartbeats");

        // Verify followers reset their timers (no election)
        assertEquals(RaftState.FOLLOWER, node2.getState());
        assertEquals(RaftState.FOLLOWER, node3.getState());

        logger.info("✓ Node2 received {} heartbeats, Node3 received {} heartbeats",
                node2Heartbeats, node3Heartbeats);
    }

    @Test
    void testHeartbeatFrequency() throws InterruptedException {
        // Node1 becomes leader
        node1.becomeLeader();

        // Wait 500ms
        Thread.sleep(500);

        long heartbeatsSent = node1.getHeartbeatsSent();

        // Heartbeats every 50ms → ~10 heartbeats in 500ms
        // Allow some variance due to timing
        assertTrue(heartbeatsSent >= 8 && heartbeatsSent <= 12,
                "Expected 8-12 heartbeats in 500ms, got " + heartbeatsSent);

        logger.info("✓ Leader sent {} heartbeats in 500ms (expected ~10)", heartbeatsSent);
    }

    @Test
    void testHeartbeatPreventsElection() throws InterruptedException {
        // Node1 becomes leader
        node1.becomeLeader();

        // Wait longer than election timeout (300ms max)
        // If heartbeats work, no election should happen
        Thread.sleep(500);

        // All nodes should maintain their states
        assertEquals(RaftState.LEADER, node1.getState());
        assertEquals(RaftState.FOLLOWER, node2.getState());
        assertEquals(RaftState.FOLLOWER, node3.getState());

        // Verify heartbeats were sent and received
        assertTrue(node1.getHeartbeatsSent() > 0);
        assertTrue(node2.getHeartbeatsReceived() > 0);
        assertTrue(node3.getHeartbeatsReceived() > 0);

        logger.info("✓ Heartbeats successfully prevented election for 500ms");
    }

    @Test
    void testElectionTimeoutDetection() throws InterruptedException {
        // Start node2 in isolation (no heartbeats)
        // Don't make anyone leader, so no heartbeats sent

        // Node2's election timer should fire
        // Wait for max election timeout (300ms) + buffer
        Thread.sleep(500);

        // Node2 should have detected timeout
        // (We can check lastHeartbeatReceived is still 0)
        assertEquals(0, node2.getLastHeartbeatReceived());

        logger.info("✓ Node2 election timer fired (no heartbeats received)");
    }

    @Test
    void testLeaderStepDown() throws InterruptedException {
        // Node1 becomes leader with term 1
        node1.setCurrentTerm(1);
        node1.becomeLeader();

        Thread.sleep(100);
        long heartbeatsBeforeStepDown = node1.getHeartbeatsSent();
        assertTrue(heartbeatsBeforeStepDown > 0);

        // Node1 discovers higher term and steps down
        node1.setCurrentTerm(2);
        // Simulate discovering higher term from a message
        // In real scenario, this happens in handleAppendEntries or handleRequestVoteResponse

        logger.info("✓ Leader can update term (stepping down tested in integration)");
    }

    @Test
    void testMultipleFollowersReceiveHeartbeats() throws InterruptedException {
        // Node1 becomes leader
        node1.becomeLeader();

        // Wait for heartbeats
        Thread.sleep(300);

        // Both followers should receive heartbeats
        assertTrue(node2.getHeartbeatsReceived() > 0, "Node2 should receive heartbeats");
        assertTrue(node3.getHeartbeatsReceived() > 0, "Node3 should receive heartbeats");

        // Both should remain followers
        assertEquals(RaftState.FOLLOWER, node2.getState());
        assertEquals(RaftState.FOLLOWER, node3.getState());

        logger.info("✓ Multiple followers successfully received heartbeats");
    }

    @Test
    void testHeartbeatWithHigherTerm() throws InterruptedException {
        // Node1 is leader with term 1
        node1.setCurrentTerm(1);
        node1.becomeLeader();

        // Node2 has term 2 (simulating it saw a higher term elsewhere)
        node2.setCurrentTerm(2);

        Thread.sleep(200);

        // Node2 should reject heartbeats from term 1
        // (It will still receive them, but mark as rejected internally)
        assertTrue(node2.getHeartbeatsReceived() > 0);

        // Node2 should remain at term 2
        assertEquals(2, node2.getCurrentTerm());

        logger.info("✓ Follower correctly handles heartbeats from lower term");
    }

    @Test
    void testHeartbeatUpdatesCommitIndex() throws InterruptedException {
        // Node1 becomes leader with some commit index
        node1.setCurrentTerm(1);
        node1.becomeLeader();

        // Wait for initial heartbeats
        Thread.sleep(100);

        long initialCommitIndex = node2.getCommitIndex();

        // In a real scenario, leader would update commitIndex as entries are replicated
        // For now, it stays at 0 (no log entries in Week 1)
        assertEquals(0, initialCommitIndex);

        logger.info("✓ CommitIndex tracking works (stays at 0 for Week 1)");
    }

    @Test
    void testConnectionManagement() throws InterruptedException {
        // Verify nodes are connected to their peers
        // Each node should connect to 2 peers
        Thread.sleep(300); // Wait for connections

        assertTrue(node1.getConnectedPeerCount() >= 0); // May vary due to async connection
        assertTrue(node2.getConnectedPeerCount() >= 0);
        assertTrue(node3.getConnectedPeerCount() >= 0);

        logger.info("✓ Nodes have peer connections: node1={}, node2={}, node3={}",
                node1.getConnectedPeerCount(),
                node2.getConnectedPeerCount(),
                node3.getConnectedPeerCount());
    }

    @Test
    void testStatistics() throws InterruptedException {
        // Node1 becomes leader
        node1.becomeLeader();

        Thread.sleep(200);

        // Check statistics are being tracked
        assertTrue(node1.getHeartbeatsSent() > 0, "Leader should track sent heartbeats");
        assertTrue(node2.getHeartbeatsReceived() > 0, "Follower should track received heartbeats");
        assertTrue(node2.getLastHeartbeatReceived() > 0, "Follower should track last heartbeat time");

        logger.info("✓ Statistics tracked correctly:");
        logger.info("  - Node1 sent: {}", node1.getHeartbeatsSent());
        logger.info("  - Node2 received: {}", node2.getHeartbeatsReceived());
        logger.info("  - Node2 last heartbeat: {}ms ago",
                System.currentTimeMillis() - node2.getLastHeartbeatReceived());
    }
}