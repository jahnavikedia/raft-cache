package com.distributed.cache.store;

/**
 * Type of key-value store command
 */
public enum CommandType {
    /**
     * Put a key-value pair into the store
     */
    PUT,

    /**
     * Delete a key from the store
     */
    DELETE,

    /**
     * Get a value from the store (read-only, typically doesn't go through Raft)
     */
    GET
}
