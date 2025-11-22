package com.distributed.cache.raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * ReadIndexManager implements the ReadIndex protocol for linearizable reads.
 *
 * The ReadIndex protocol ensures that reads see all committed writes by:
 * 1. Recording the current commitIndex
 * 2. Confirming leadership via heartbeat to majority
 * 3. Waiting for state machine to apply up to that index
 *
 * This prevents reading stale data from a partitioned old leader.
 */
public class ReadIndexManager {
    private static final Logger logger = LoggerFactory.getLogger(ReadIndexManager.class);
    private static final long CONFIRMATION_TIMEOUT_MS = 500;

    private final RaftNode raftNode;

    public ReadIndexManager(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    /**
     * Confirms read index using the ReadIndex protocol.
     *
     * Steps:
     * 1. Check if this node is leader
     * 2. Record current commitIndex as readIndex
     * 3. Send heartbeat and confirm majority
     * 4. Return readIndex if still leader
     *
     * @return CompletableFuture with readIndex
     * @throws NotLeaderException if not leader or loses leadership
     */
    public CompletableFuture<Long> confirmReadIndex() {
        // Step 1: Check if leader
        if (!raftNode.isLeader()) {
            String leaderId = raftNode.getVotedFor();
            throw new NotLeaderException("Not leader, cannot serve reads", leaderId);
        }

        // Step 2: Record current commitIndex
        long readIndex = raftNode.getCommitIndex();

        logger.debug("Starting ReadIndex confirmation: readIndex={}, term={}",
                    readIndex, raftNode.getCurrentTerm());

        // Step 3: Send heartbeat and confirm leadership
        return raftNode.sendHeartbeatAndConfirmLeadership()
            .orTimeout(CONFIRMATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .thenApply(confirmed -> {
                if (!confirmed) {
                    throw new NotLeaderException("Lost leadership during read confirmation");
                }

                // Still leader, return readIndex
                logger.debug("ReadIndex confirmed: readIndex={}", readIndex);
                return readIndex;
            })
            .exceptionally(ex -> {
                logger.warn("ReadIndex confirmation failed", ex);
                throw new NotLeaderException("Read confirmation failed: " + ex.getMessage());
            });
    }
}
