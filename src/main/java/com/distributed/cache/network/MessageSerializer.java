package com.distributed.cache.network;

import com.distributed.cache.raft.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MessageSerializer handles JSON serialization and deserialization of Raft
 * messages.
 *
 * Uses Jackson for JSON processing, which allows us to:
 * - Convert Message objects to JSON strings for network transmission
 * - Convert JSON strings back to Message objects when receiving
 *
 * This is used by NetworkBase for all message transmission.
 */
public class MessageSerializer {
    private static final Logger logger = LoggerFactory.getLogger(MessageSerializer.class);

    // Jackson ObjectMapper - thread-safe and reusable
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // Configure ObjectMapper for cleaner JSON output
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Serialize a Message object to a JSON string
     *
     * @param message The message to serialize
     * @return JSON string representation
     * @throws Exception if serialization fails
     */
    public static String serialize(Message message) throws Exception {
        try {
            String json = objectMapper.writeValueAsString(message);
            logger.trace("Serialized message: {}", json);
            return json;
        } catch (Exception e) {
            logger.error("Failed to serialize message: {}", message, e);
            throw e;
        }
    }

    /**
     * Deserialize a JSON string to a Message object
     *
     * @param json The JSON string to deserialize
     * @return The deserialized Message object
     * @throws Exception if deserialization fails
     */
    public static Message deserialize(String json) throws Exception {
        try {
            Message message = objectMapper.readValue(json, Message.class);
            logger.trace("Deserialized message: {}", message);
            return message;
        } catch (Exception e) {
            logger.error("Failed to deserialize JSON: {}", json, e);
            throw e;
        }
    }

    /**
     * Helper method to create an AppendEntries message (heartbeat)
     * This is useful for Person B (heartbeat feature)
     */
    public static Message createHeartbeat(String leaderId, long term, long leaderCommit) {
        Message message = new Message(Message.MessageType.APPEND_ENTRIES, term, leaderId);
        message.setLeaderId(leaderId);
        message.setLeaderCommit(leaderCommit);
        message.setPrevLogIndex(0);
        message.setPrevLogTerm(0);
        message.setSenderTerm(term); // ensure senderTerm propagated
        return message;
    }

    /**
     * Helper method to create an AppendEntries response
     * This is useful for Person B (heartbeat feature)
     */
    public static Message createHeartbeatResponse(String senderId, long term, boolean success) {
        Message message = new Message(Message.MessageType.APPEND_ENTRIES_RESPONSE, term, senderId);
        message.setSuccess(success);
        message.setSenderTerm(term); // ensure senderTerm propagated
        return message;
    }

    /**
     * Helper method to create a RequestVote message
     * This is useful for Person A (election feature)
     */
    public static Message createRequestVote(String candidateId, long term, long lastLogIndex, long lastLogTerm) {
        Message message = new Message(Message.MessageType.REQUEST_VOTE, term, candidateId);
        message.setCandidateId(candidateId);
        message.setLastLogIndex(lastLogIndex);
        message.setLastLogTerm(lastLogTerm);
        message.setSenderTerm(term); // ensure senderTerm propagated
        return message;
    }

    /**
     * Helper method to create a RequestVote response
     * This is useful for Person A (election feature)
     */
    public static Message createRequestVoteResponse(String senderId, long term, boolean voteGranted) {
        Message message = new Message(Message.MessageType.REQUEST_VOTE_RESPONSE, term, senderId);
        message.setSuccess(voteGranted);
        message.setSenderTerm(term); // ensure senderTerm propagated
        return message;
    }
}
