package com.example.server.model;


public class JoinRequest {
    private String userId;
    private String code;
    private boolean isEditor;

    // Constructors
    public JoinRequest() {}

    public JoinRequest(String userId, String code, boolean isEditor) {
        this.userId = userId;
        this.code = code;
        this.isEditor = isEditor;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isEditor() {
        return isEditor;
    }

    public void setEditor(boolean editor) {
        isEditor = editor;
    }
}