package com.distributed.cache.raft;

/**
 * Type of log entry in the Raft log
 */
public enum LogEntryType {
    /**
     * Regular command to be applied to state machine
     */
    COMMAND,

    /**
     * No-operation entry used by leaders to commit entries from previous terms
     */
    NO_OP,

    /**
     * Configuration change entry (for cluster membership changes)
     */
    CONFIGURATION
}
