package com.distributed.cache.raft;

/**
 * Exception thrown when a non-leader node attempts to perform a leader-only operation.
 * This is used in the ReadIndex protocol to reject read requests from followers.
 */
public class NotLeaderException extends RuntimeException {
    private final String currentLeaderId;

    public NotLeaderException(String message) {
        super(message);
        this.currentLeaderId = null;
    }

    public NotLeaderException(String message, String currentLeaderId) {
        super(message);
        this.currentLeaderId = currentLeaderId;
    }

    public String getCurrentLeaderId() {
        return currentLeaderId;
    }
}
