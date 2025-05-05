package org.example.client.model;

import com.google.gson.JsonObject;

public class ClientMessage {
    private String type;
    private String sessionId;
    private String userId;
    private JsonObject data;

    public ClientMessage(String type, String sessionId, String userId, Object data) {
        this.type = type;
        this.sessionId = sessionId;
        this.userId = userId;
        this.data = new JsonObject();
        // Add data fields based on operation type
        if (data instanceof JsonObject) {
            this.data = (JsonObject) data;
        }
    }

    // Getters
    public String getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public JsonObject getData() {
        return data;
    }
}