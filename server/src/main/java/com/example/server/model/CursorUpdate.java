package com.example.server.model;

public class CursorUpdate {
    private String userId;
    private int position;

    // Default constructor (required for JSON deserialization)
    public CursorUpdate() {}

    // Constructor
    public CursorUpdate(String userId, int position) {
        this.userId = userId;
        this.position = position;
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}