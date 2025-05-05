package com.example.server.model;
//similart to CRTDOperation but to hide its complexity from clint
public class ClientOperation {
    private String userId;
    private String type;  // "INSERT" or "DELETE"
    private int position;
    private String text;  // For insert operations
    private String targetUserId;  // For delete operations
    private String targetClock;   // For delete operations

    // Default constructor
    public ClientOperation() {}

    // Constructor for insert operations
    public ClientOperation(String userId, String type, int position, String text) {
        this.userId = userId;
        this.type = type;
        this.position = position;
        this.text = text;
    }

    // Constructor for delete operations
    public ClientOperation(String userId, String type, String targetUserId, String targetClock) {
        this.userId = userId;
        this.type = type;
        this.targetUserId = targetUserId;
        this.targetClock = targetClock;
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getTargetClock() {
        return targetClock;
    }

    public void setTargetClock(String targetClock) {
        this.targetClock = targetClock;
    }
}