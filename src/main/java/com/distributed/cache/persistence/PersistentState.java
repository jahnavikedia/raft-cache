package com.distributed.cache.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * PersistentState handles durable storage of Raft node metadata:
 * - currentTerm
 * - votedFor
 *
 * Each node stores its state in:
 * data/node-{nodeId}/persistent-state.properties
 *
 * The file is updated atomically using a temporary write-and-rename pattern.
 * All public methods are synchronized for thread safety.
 */
public class PersistentState {

    private static final Logger logger = LoggerFactory.getLogger(PersistentState.class);

    private final String nodeId;
    private final Path stateDir;
    private final Path stateFile;
    private final Properties props = new Properties();

    private long storedTerm = 0;
    private String storedVotedFor = null;

    public PersistentState(String nodeId) {
        this.nodeId = nodeId;
        this.stateDir = Path.of("data", "node-" + nodeId);
        this.stateFile = stateDir.resolve("persistent-state.properties");

        loadState(); // Initialize on creation
    }

    /** Load state from disk or create defaults if missing. */
    public synchronized void loadState() {
        try {
            if (!Files.exists(stateDir)) {
                Files.createDirectories(stateDir);
            }

            if (Files.exists(stateFile)) {
                try (InputStream in = Files.newInputStream(stateFile)) {
                    props.load(in);
                    storedTerm = Long.parseLong(props.getProperty("currentTerm", "0"));
                    storedVotedFor = props.getProperty("votedFor", null);
                }
                logger.info("Node {} recovered state from disk: term={}, votedFor={}",
                        nodeId, storedTerm, storedVotedFor);
            } else {
                storedTerm = 0;
                storedVotedFor = null;
                saveState(storedTerm, storedVotedFor);
                logger.info("Node {} initialized new state file (term=0, votedFor=null)", nodeId);
            }

        } catch (IOException e) {
            logger.error("Failed to load persistent state for node {}", nodeId, e);
            storedTerm = 0;
            storedVotedFor = null;
        }
    }

    /** Atomically save both term and votedFor. */
    public synchronized void saveState(long term, String votedFor) {
        props.setProperty("currentTerm", Long.toString(term));
        if (votedFor != null)
            props.setProperty("votedFor", votedFor);
        else
            props.remove("votedFor");

        try {
            Path tmpFile = stateFile.resolveSibling("persistent-state.tmp");
            try (OutputStream out = Files.newOutputStream(tmpFile)) {
                props.store(out, "Raft persistent state for " + nodeId);
            }
            Files.move(tmpFile, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            storedTerm = term;
            storedVotedFor = votedFor;
            logger.debug("Node {} persisted state (term={}, votedFor={})", nodeId, term, votedFor);
        } catch (IOException e) {
            logger.error("Failed to persist state for node {}", nodeId, e);
        }
    }

    /** Save only term, retaining votedFor. */
    public synchronized void saveTerm(long term) {
        saveState(term, storedVotedFor);
    }

    /** Save only votedFor, retaining term. */
    public synchronized void saveVotedFor(String votedFor) {
        saveState(storedTerm, votedFor);
    }

    /** Get the currently stored term. */
    public synchronized long getStoredTerm() {
        return storedTerm;
    }

    /** Get the currently stored votedFor. */
    public synchronized String getStoredVotedFor() {
        return storedVotedFor;
    }

    /** Clear both term and vote (used on reset). */
    public synchronized void reset() {
        saveState(0, null);
        logger.info("Node {} state reset to defaults", nodeId);
    }
}
