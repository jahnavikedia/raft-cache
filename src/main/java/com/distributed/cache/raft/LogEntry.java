package com.distributed.cache.raft;

/**
 * Represents a single entry in the Raft log
 */
public class LogEntry {
    private final long term;      // Term when entry was received by leader
    private final long index;     // Position in the log
    private final String command; // Command to execute (e.g., PUT key:value)
    
    public LogEntry(long term, long index, String command) {
        this.term = term;
        this.index = index;
        this.command = command;
    }
    
    public long getTerm() {
        return term;
    }
    
    public long getIndex() {
        return index;
    }
    
    public String getCommand() {
        return command;
    }
    
    @Override
    public String toString() {
        return String.format("LogEntry{term=%d, index=%d, command='%s'}", 
                           term, index, command);
    }
}
