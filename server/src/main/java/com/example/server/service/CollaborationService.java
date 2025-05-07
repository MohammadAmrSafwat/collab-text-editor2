package com.example.server.service;

import com.example.server.model.*;
import com.example.server.service.crdt.*;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CollaborationService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();

    public Session joinSession(String sessionId, String userId, String code, boolean isEditor) {
        // Use computeIfAbsent for thread-safe initialization
        Session session = sessions.computeIfAbsent(sessionId, id -> new Session(id));

        if (isEditor && code.equals(session.getEditorCode())) {
            session.addEditor(userId);
        } else if (code.equals(session.getViewerCode())) {
            session.addViewer(userId);
        }
        return session;
    }
    public void addSession(Session session) {
        sessions.put(session.getSessionId(), session);
    }
    public Session getSessionByCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }

        // Search through all sessions to find matching code
        for (Session session : sessions.values()) {
            if (code.equals(session.getEditorCode()) || code.equals(session.getViewerCode())) {
                return session;
            }

            // Handle cases where code includes document ID prefix
            if (code.startsWith(session.getSessionId())) {
                String codeSuffix = code.substring(session.getSessionId().length());
                if (("-view".equals(codeSuffix) && session.getViewerCode().equals(code)) ||
                        ("-edit".equals(codeSuffix) && session.getEditorCode().equals(code))) {
                    return session;
                }
            }
        }
        return null;
    }
    public CRDTOperation processOperation(String sessionId, String userId, ClientOperation operation) {
        Session session = sessions.get(sessionId);
        if (session == null || !session.isEditor(userId)) {
            throw new SecurityException("Operation not permitted");
        }

        CRDTOperation op = convertToCRDTOperation(operation);
        session.getCrdt().applyOperation(op);
        return op;
    }

    private CRDTOperation convertToCRDTOperation(ClientOperation operation) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return new CRDTOperation(
                operation.getType().equals("INSERT") ?
                        CRDTOperation.OperationType.INSERT :
                        CRDTOperation.OperationType.DELETE,
                operation.getUserId(),
                timestamp,
                operation.getText() != null ? operation.getText().charAt(0) : null,
                null, // Parent will be calculated in CRDT
                operation.getType().equals("DELETE") ?
                        new CRDTOperation.NodeId(operation.getTargetUserId(), operation.getTargetClock()) :
                        null
        );
    }

    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }


}