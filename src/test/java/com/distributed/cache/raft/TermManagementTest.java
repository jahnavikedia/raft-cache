package com.distributed.cache.raft;

import com.distributed.cache.persistence.PersistentState;
import org.junit.jupiter.api.*;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-style tests for Raft term management and safety rules
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TermManagementTest {

    private RaftNode node;

    @BeforeEach
    void setUp() {
        File dir = new File("data/node-testTerm");
        if (dir.exists()) {
            File stateFile = new File(dir, "persistent-state.properties");
            if (stateFile.exists())
                stateFile.delete();
        }

        node = new RaftNode("testTerm", 9001);
        node.testInitializeForUnitTests(); // <-- ADD THIS LINE
    }

    @AfterEach
    void tearDown() {
        node.shutdown();
    }

    @Test
    @Order(1)
    void testUpdateTermHigherReplacesOldTermAndBecomesFollower() {
        node.setCurrentTerm(2);
        node.becomeLeader();
        node.testUpdateTerm(5);
        assertEquals(5, node.getCurrentTerm());
        assertEquals(RaftState.FOLLOWER, node.getState());
        assertNull(node.getVotedFor());
    }

    @Test
    @Order(2)
    void testUpdateTermLowerIsIgnored() {
        node.setCurrentTerm(10);
        node.testUpdateTerm(5);
        assertEquals(10, node.getCurrentTerm());
    }

    @Test
    @Order(3)
    void testVoteGrantingPersistsVotedFor() {
        node.setCurrentTerm(1);
        node.testBecomeFollower();
        Message msg = new Message(Message.MessageType.REQUEST_VOTE, 1, "node2");
        msg.setCandidateId("node2");

        // Should grant and persist vote
        node.testHandleRequestVote(msg);

        PersistentState ps = new PersistentState("testTerm");
        assertEquals("node2", ps.getStoredVotedFor());
    }

    @Test
    @Order(4)
    void testLeaderStepsDownOnHigherTerm() {
        node.setCurrentTerm(2);
        node.becomeLeader();
        node.testUpdateTerm(5);
        assertEquals(RaftState.FOLLOWER, node.getState());
    }

    @Test
    @Order(5)
    void testPersistentStateReloadsAfterRestart() {
        PersistentState ps = new PersistentState("testTerm");
        ps.saveState(7, "node3");

        RaftNode restarted = new RaftNode("testTerm", 9001);
        assertEquals(7, restarted.getCurrentTerm());
        assertEquals("node3", restarted.getVotedFor());
    }

    @Test
    @Order(6)
    void testFollowerRejectsOldLeaderHeartbeat() {
        node.setCurrentTerm(5);
        Message oldHeartbeat = new Message(Message.MessageType.APPEND_ENTRIES, 3, "oldLeader");
        node.testHandleAppendEntries(oldHeartbeat);
        assertEquals(5, node.getCurrentTerm());
    }
}
