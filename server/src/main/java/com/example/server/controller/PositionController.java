package com.example.server.controller;

import com.example.server.model.Session;
import com.example.server.service.CollaborationService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class PositionController {
    private final CollaborationService collaborationService;
    private final SimpMessagingTemplate messagingTemplate;

    public PositionController(CollaborationService collaborationService,
                               SimpMessagingTemplate messagingTemplate) {
        this.collaborationService = collaborationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/document/position")
    public void handlePositionUpdate(@Payload String message) {
        try {
            System.out.println("Received position: " + message);
            JsonObject payload = JsonParser.parseString(message).getAsJsonObject();
            String sessionId = payload.get("sessionId").getAsString();
            String userId = payload.get("userId").getAsString();
            boolean isEditor = payload.get("isEditor").getAsBoolean();
            int lineNumber = payload.get("lineNumber").getAsInt();

            Session session = collaborationService.getSession(sessionId);
            if (session != null) {
                if (isEditor) {
                    session.updateEditorLinePosition(userId, lineNumber);
                }
                broadcastPresenceUpdate(session);
            }
        } catch (Exception e) {
            System.err.println("Error processing position update: " + e.getMessage());
        }
    }

    @MessageMapping("/document/join")
    public void handleUserJoin(@Payload String message) {
        try {
            JsonObject payload = JsonParser.parseString(message).getAsJsonObject();
            String sessionId = payload.get("sessionId").getAsString();
            String userId = payload.get("userId").getAsString();
            boolean isEditor = payload.get("isEditor").getAsBoolean();

            Session session = collaborationService.getSession(sessionId);
            if (session != null) {
                session.addUser(userId, isEditor);
                broadcastPresenceUpdate(session);
            }
        } catch (Exception e) {
            System.err.println("Error processing user join: " + e.getMessage());
        }
    }

    @MessageMapping("/document/leave")
    public void handleUserLeave(@Payload String message) {
        try {
            JsonObject payload = JsonParser.parseString(message).getAsJsonObject();
            String sessionId = payload.get("sessionId").getAsString();
            String userId = payload.get("userId").getAsString();

            Session session = collaborationService.getSession(sessionId);
            if (session != null) {
                session.removeUser(userId);
                broadcastPresenceUpdate(session);
            }
        } catch (Exception e) {
            System.err.println("Error processing user leave: " + e.getMessage());
        }
    }

    private void broadcastPresenceUpdate(Session session) {
        JsonObject presenceUpdate = new JsonObject();
        presenceUpdate.addProperty("type", "USER_PRESENCE");
        presenceUpdate.addProperty("sessionId", session.getSessionId());

        // Add all users
        JsonArray usersArray = new JsonArray();
        for (String userId : session.getAllUsers()) {
            JsonObject userObj = new JsonObject();
            userObj.addProperty("userId", userId);
            userObj.addProperty("isEditor", session.getEditors().contains(userId));

            // Add line position only for editors
            if (session.getEditors().contains(userId)) {
                Integer lineNumber = session.getEditorPositions().get(userId);
                userObj.addProperty("lineNumber", lineNumber != null ? lineNumber : 0);
            }

            usersArray.add(userObj);
        }

        presenceUpdate.add("activeUsers", usersArray);
        System.out.println("Sending presence update: " + presenceUpdate.toString());
        messagingTemplate.convertAndSend(
                "/topic/presence." + session.getSessionId(),
                presenceUpdate.toString()
        );
    }
}