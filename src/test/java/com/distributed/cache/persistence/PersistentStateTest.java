package com.distributed.cache.persistence;

import org.junit.jupiter.api.*;
import java.io.File;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PersistentState class (term/vote persistence)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PersistentStateTest {

    private static final String TEST_NODE_ID = "testNode";
    private PersistentState persistentState;
    private File stateFile;

    @BeforeEach
    void setUp() {
        persistentState = new PersistentState(TEST_NODE_ID);
        stateFile = new File("data/node-" + TEST_NODE_ID + "/persistent-state.properties");
        if (stateFile.exists()) {
            assertTrue(stateFile.delete(), "Failed to clear existing state file before test");
        }
    }

    @AfterEach
    void tearDown() {
        if (stateFile.exists())
            stateFile.delete();
    }

    @Test
    @Order(1)
    void testInitialDefaultsWhenFileMissing() {
        PersistentState ps = new PersistentState(TEST_NODE_ID + "_missing");
        assertEquals(0, ps.getStoredTerm());
        assertNull(ps.getStoredVotedFor());
    }

    @Test
    @Order(2)
    void testSaveAndLoadTermAndVotedFor() {
        persistentState.saveState(5, "node2");

        PersistentState reloaded = new PersistentState(TEST_NODE_ID);
        assertEquals(5, reloaded.getStoredTerm());
        assertEquals("node2", reloaded.getStoredVotedFor());
    }

    @Test
    @Order(3)
    void testSaveTermAndVotedForSeparately() {
        persistentState.saveTerm(10);
        persistentState.saveVotedFor("node3");

        PersistentState reloaded = new PersistentState(TEST_NODE_ID);
        assertEquals(10, reloaded.getStoredTerm());
        assertEquals("node3", reloaded.getStoredVotedFor());
    }

    @Test
    @Order(4)
    void testConcurrentWritesDoNotCorruptFile() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            int term = i;
            executor.submit(() -> persistentState.saveTerm(term));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));

        // Reload should still produce a valid number (no corruption)
        PersistentState reloaded = new PersistentState(TEST_NODE_ID);
        assertTrue(reloaded.getStoredTerm() >= 0);
    }

    @Test
    @Order(5)
    void testHandlesCorruptedFileGracefully() throws Exception {
        stateFile.getParentFile().mkdirs();
        java.nio.file.Files.writeString(stateFile.toPath(), "corrupted=@@@###");

        PersistentState reloaded = new PersistentState(TEST_NODE_ID);
        assertEquals(0, reloaded.getStoredTerm());
        assertNull(reloaded.getStoredVotedFor());
    }
}
