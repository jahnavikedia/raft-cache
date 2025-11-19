package com.distributed.cache.raft.api;

public class ClientResponse {
    private final boolean success;
    private final String value;
    private final String errorMessage;
    private final String leaderId;

    public ClientResponse(boolean success, String value, String errorMessage, String leaderId) {
        this.success = success;
        this.value = value;
        this.errorMessage = errorMessage;
        this.leaderId = leaderId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getValue() {
        return value;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getLeaderId() {
        return leaderId;
    }
}
