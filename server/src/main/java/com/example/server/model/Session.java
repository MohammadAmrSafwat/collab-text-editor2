package com.example.server.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.example.server.service.crdt.CRDT;

public class Session {
    private String sessionId;
    private String editorCode;
    private String viewerCode;
    private String docide;
    private String docidv;
    private Set<String> editors = ConcurrentHashMap.newKeySet();
    private Set<String> viewers = ConcurrentHashMap.newKeySet();

    // Track editor line positions only
    private Map<String, Integer> editorLinePositions = new ConcurrentHashMap<>();

    private CRDT crdt = new CRDT(); // tree-based CRDT for this session

    public Session(String sessionId) {
        this.sessionId = sessionId;
        String documentId = "doc_" + System.currentTimeMillis();
        docide = "edit-" + documentId;
        docidv ="view-" + documentId;
        this.editorCode =   docide.substring(0, docide.length() - 3);
        this.viewerCode =  docidv.substring(0, docidv.length() - 3) ;
    }
    public void updateEditorLinePosition(String userId, int lineNumber) {
        if (editors.contains(userId)) {
            editorLinePositions.put(userId, lineNumber);
        }
    }
    public Set<String> getAllUsers() {
        Set<String> allUsers = new HashSet<>();
        allUsers.addAll(editors);
        allUsers.addAll(viewers);
        return allUsers;
    }
    public Map<String, Integer> getEditorPositions() {
        return new HashMap<>(editorLinePositions);
    }

    private String generateRandomCode(int lenght) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"; // Letters AND numbers
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lenght; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }
    public void addUser(String userId, boolean isEditor) {
        if (isEditor) {
            editors.add(userId);
        } else {
            viewers.add(userId);
        }
    }

    // Remove user from session
    public void removeUser(String userId) {
        editors.remove(userId);
        viewers.remove(userId);
        editorLinePositions.remove(userId);
    }
    public String getSessionId() {
        return sessionId;
    }

    public String getEditorCode() {
        return editorCode;
    }

    public String getViewerCode() {
        return viewerCode;
    }

    public Set<String> getEditors() {
        return editors;
    }

    public Set<String> getViewers() {
        return viewers;
    }

    public void addEditor(String userId) {
        editors.add(userId);
    }

    public void addViewer(String userId) {
        viewers.add(userId);
    }

    public boolean isEditor(String userId) {
        return editors.contains(userId);
    }

    public boolean isViewer(String userId) {
        return viewers.contains(userId);
    }

    public boolean isParticipant(String userId) {
        return isEditor(userId) || isViewer(userId);
    }

    public CRDT getCrdt() {
        return crdt;
    }
}
