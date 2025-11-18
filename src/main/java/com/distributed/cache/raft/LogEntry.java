package com.distributed.cache.raft;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * Represents a single entry in the Raft log.
 *
 * Each log entry contains:
 * - index: position in the log (1-indexed)
 * - term: the term when the entry was created by the leader
 * - command: the serialized command to apply to the state machine (JSON)
 * - type: the type of entry (COMMAND, NO_OP, CONFIGURATION)
 * - timestamp: when the entry was created
 */
public class LogEntry {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @JsonProperty("index")
    private final long index;

    @JsonProperty("term")
    private final int term;

    @JsonProperty("command")
    private final String command;

    @JsonProperty("type")
    private final LogEntryType type;

    @JsonProperty("timestamp")
    private final long timestamp;

    // Constructor for Jackson deserialization
    public LogEntry(
            @JsonProperty("index") long index,
            @JsonProperty("term") int term,
            @JsonProperty("command") String command,
            @JsonProperty("type") LogEntryType type,
            @JsonProperty("timestamp") long timestamp) {
        this.index = index;
        this.term = term;
        this.command = command;
        this.type = type;
        this.timestamp = timestamp;
    }

    // Convenience constructor for creating new entries
    public LogEntry(long index, int term, String command, LogEntryType type) {
        this(index, term, command, type, System.currentTimeMillis());
    }

    public long getIndex() {
        return index;
    }

    public int getTerm() {
        return term;
    }

    public String getCommand() {
        return command;
    }

    public LogEntryType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Serialize this log entry to JSON string
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize LogEntry to JSON", e);
        }
    }

    /**
     * Deserialize a log entry from JSON string
     */
    public static LogEntry fromJson(String json) {
        try {
            return objectMapper.readValue(json, LogEntry.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize LogEntry from JSON: " + json, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogEntry logEntry = (LogEntry) o;
        return index == logEntry.index &&
                term == logEntry.term &&
                timestamp == logEntry.timestamp &&
                Objects.equals(command, logEntry.command) &&
                type == logEntry.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, term, command, type, timestamp);
    }

    @Override
    public String toString() {
        return String.format("LogEntry{index=%d, term=%d, type=%s, command='%s', timestamp=%d}",
                index, term, type, command, timestamp);
    }
}
