package com.distributed.cache.replication;

import com.distributed.cache.raft.RaftNode;
import com.distributed.cache.raft.RaftState;
import com.distributed.cache.store.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Manual test for log replication and key-value store integration.
 *
 * This test:
 * 1. Starts 3 Raft nodes
 * 2. Waits for leader election
 * 3. Performs PUT operation on the leader
 * 4. Waits for replication
 * 5. Verifies all followers have the value
 */
public class ManualReplicationTest {
    private static final Logger logger = LoggerFactory.getLogger(ManualReplicationTest.class);

    private RaftNode node1;
    private RaftNode node2;
    private RaftNode node3;

    @BeforeEach
    public void setUp() throws InterruptedException {
        logger.info("=== Setting up 3-node Raft cluster ===");

        // Create nodes
        node1 = new RaftNode("node1", 7001);
        node2 = new RaftNode("node2", 7002);
        node3 = new RaftNode("node3", 7003);

        // Configure peers
        Map<String, String> peers1 = new HashMap<>();
        peers1.put("node2", "localhost:7002");
        peers1.put("node3", "localhost:7003");

        Map<String, String> peers2 = new HashMap<>();
        peers2.put("node1", "localhost:7001");
        peers2.put("node3", "localhost:7003");

        Map<String, String> peers3 = new HashMap<>();
        peers3.put("node1", "localhost:7001");
        peers3.put("node2", "localhost:7002");

        node1.configurePeers(peers1);
        node2.configurePeers(peers2);
        node3.configurePeers(peers3);

        // Start nodes
        logger.info("Starting node1...");
        node1.start();
        Thread.sleep(100);

        logger.info("Starting node2...");
        node2.start();
        Thread.sleep(100);

        logger.info("Starting node3...");
        node3.start();
        Thread.sleep(100);

        logger.info("All nodes started. Waiting for leader election...");
    }

    @AfterEach
    public void tearDown() {
        logger.info("=== Shutting down cluster ===");
        if (node1 != null) node1.shutdown();
        if (node2 != null) node2.shutdown();
        if (node3 != null) node3.shutdown();
    }

    @Test
    public void testLogReplication() throws Exception {
        logger.info("=== Test: Log Replication ===");

        // Wait for leader election (up to 10 seconds)
        RaftNode leader = waitForLeaderElection(10000);
        assertNotNull(leader, "A leader should be elected");
        logger.info("Leader elected: {}", leader.getNodeId());

        // Get the key-value store from the leader
        KeyValueStore leaderStore = leader.getKvStore();

        // Perform PUT operation
        logger.info("Performing PUT operation: key1 = value1");
        CompletableFuture<String> putFuture = leaderStore.put("key1", "value1", "test-client", 1);

        // Wait for the PUT to complete
        String result = putFuture.get();
        assertEquals("value1", result);
        logger.info("PUT operation completed successfully");

        // Wait for replication (2 seconds)
        logger.info("Waiting 2 seconds for replication...");
        Thread.sleep(2000);

        // Verify on all nodes
        logger.info("Verifying replication on all nodes...");
        String value1 = node1.getKvStore().get("key1");
        String value2 = node2.getKvStore().get("key1");
        String value3 = node3.getKvStore().get("key1");

        logger.info("Node1 has key1 = {}", value1);
        logger.info("Node2 has key1 = {}", value2);
        logger.info("Node3 has key1 = {}", value3);

        // All nodes should have the value
        assertEquals("value1", value1, "Node1 should have the replicated value");
        assertEquals("value1", value2, "Node2 should have the replicated value");
        assertEquals("value1", value3, "Node3 should have the replicated value");

        logger.info("SUCCESS: Log replication working!");

        // Perform another PUT
        logger.info("Performing second PUT operation: key2 = value2");
        putFuture = leaderStore.put("key2", "value2", "test-client", 2);
        result = putFuture.get();
        assertEquals("value2", result);

        // Wait for replication
        Thread.sleep(2000);

        // Verify again
        value1 = node1.getKvStore().get("key2");
        value2 = node2.getKvStore().get("key2");
        value3 = node3.getKvStore().get("key2");

        logger.info("Node1 has key2 = {}", value1);
        logger.info("Node2 has key2 = {}", value2);
        logger.info("Node3 has key2 = {}", value3);

        assertEquals("value2", value1, "Node1 should have the second replicated value");
        assertEquals("value2", value2, "Node2 should have the second replicated value");
        assertEquals("value2", value3, "Node3 should have the second replicated value");

        logger.info("SUCCESS: Multiple log entries replicated correctly!");

        // Verify log indices
        logger.info("Log stats - Node1: {} entries, commitIndex: {}",
                node1.getRaftLog().size(), node1.getRaftLog().getCommitIndex());
        logger.info("Log stats - Node2: {} entries, commitIndex: {}",
                node2.getRaftLog().size(), node2.getRaftLog().getCommitIndex());
        logger.info("Log stats - Node3: {} entries, commitIndex: {}",
                node3.getRaftLog().size(), node3.getRaftLog().getCommitIndex());

        // All logs should have at least 3 entries (NO_OP + 2 PUTs)
        assertTrue(node1.getRaftLog().size() >= 3, "Node1 should have at least 3 log entries");
        assertTrue(node2.getRaftLog().size() >= 3, "Node2 should have at least 3 log entries");
        assertTrue(node3.getRaftLog().size() >= 3, "Node3 should have at least 3 log entries");
    }

    /**
     * Wait for a leader to be elected
     */
    private RaftNode waitForLeaderElection(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            if (node1.getState() == RaftState.LEADER) return node1;
            if (node2.getState() == RaftState.LEADER) return node2;
            if (node3.getState() == RaftState.LEADER) return node3;

            Thread.sleep(100);
        }

        logger.error("No leader elected within {} ms", timeoutMs);
        logger.error("Node1 state: {}, term: {}", node1.getState(), node1.getCurrentTerm());
        logger.error("Node2 state: {}, term: {}", node2.getState(), node2.getCurrentTerm());
        logger.error("Node3 state: {}, term: {}", node3.getState(), node3.getCurrentTerm());

        return null;
    }
}
