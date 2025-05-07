package com.example.server.service;

import com.example.server.model.*;
import com.example.server.service.crdt.*;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
//conatin all the sessions
@Service
public class CollaborationService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();
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
    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }


}