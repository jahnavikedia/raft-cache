package com.distributed.cache.store;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents a command to be applied to the key-value store.
 *
 * Commands are serialized to JSON and stored in the Raft log.
 * Each command has a client ID and sequence number to prevent duplicate operations.
 */
public class KeyValueCommand {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @JsonProperty("type")
    private final CommandType type;

    @JsonProperty("key")
    private final String key;

    @JsonProperty("value")
    private final String value;

    @JsonProperty("timestamp")
    private final long timestamp;

    @JsonProperty("clientId")
    private final String clientId;

    @JsonProperty("sequenceNumber")
    private final long sequenceNumber;

    public KeyValueCommand(
            @JsonProperty("type") CommandType type,
            @JsonProperty("key") String key,
            @JsonProperty("value") String value,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("clientId") String clientId,
            @JsonProperty("sequenceNumber") long sequenceNumber) {
        this.type = type;
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
        this.clientId = clientId;
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Create a PUT command
     */
    public static KeyValueCommand put(String key, String value, String clientId, long sequenceNumber) {
        return new KeyValueCommand(
                CommandType.PUT,
                key,
                value,
                System.currentTimeMillis(),
                clientId,
                sequenceNumber
        );
    }

    /**
     * Create a DELETE command
     */
    public static KeyValueCommand delete(String key, String clientId, long sequenceNumber) {
        return new KeyValueCommand(
                CommandType.DELETE,
                key,
                null,
                System.currentTimeMillis(),
                clientId,
                sequenceNumber
        );
    }

    /**
     * Create a GET command (rarely used, as reads are typically local)
     */
    public static KeyValueCommand get(String key, String clientId, long sequenceNumber) {
        return new KeyValueCommand(
                CommandType.GET,
                key,
                null,
                System.currentTimeMillis(),
                clientId,
                sequenceNumber
        );
    }

    public CommandType getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getClientId() {
        return clientId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Serialize to JSON
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize KeyValueCommand", e);
        }
    }

    /**
     * Deserialize from JSON
     */
    public static KeyValueCommand fromJson(String json) {
        try {
            return objectMapper.readValue(json, KeyValueCommand.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize KeyValueCommand: " + json, e);
        }
    }

    @Override
    public String toString() {
        return String.format("KeyValueCommand{type=%s, key='%s', value='%s', clientId='%s', seq=%d, timestamp=%d}",
                type, key, value, clientId, sequenceNumber, timestamp);
    }
}
