package com.example.server.model;

public class SessionState {
    private final String content;
    private final String sessionId;

    public SessionState(String content, String sessionId) {
        this.content = content;
        this.sessionId = sessionId;
    }

    // Getters
    public String getContent() {
        return content;
    }

    public String getSessionId() {
        return sessionId;
    }
}
