package com.example.server.controller;

import com.example.server.model.Session;
import com.example.server.service.CollaborationService;
import com.example.server.service.crdt.CRDTOperation;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class OperationController {
    private final CollaborationService collaborationService;
    private final SimpMessagingTemplate messagingTemplate;

    public OperationController(CollaborationService collaborationService,
                               SimpMessagingTemplate messagingTemplate) {
        this.collaborationService = collaborationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/document/operation") // Matches client's destination
    public void handleOperation(@Payload String message) {
        try {
            System.out.println("Received operation: " + message);
            JsonObject payload = JsonParser.parseString(message).getAsJsonObject();
            String type = payload.get("type").getAsString();
            String sessionId = payload.get("sessionId").getAsString();
            String userId = payload.get("userId").getAsString();
            JsonObject data = payload.getAsJsonObject("data");
            System.out.println("data: " + data);
            Session session = collaborationService.getSession(sessionId);
            if (session == null) return;

            synchronized (session) {
                if ("insert".equals(type)) {
                    int position = data.get("position").getAsInt();
                    if (position < 0 || position > session.getCrdt().getLength()) {
                        System.err.println("Invalid insert position: " + position);
                        return;
                    }
                    char character = data.get("character").getAsString().charAt(0);
                    CRDTOperation op = session.getCrdt().createInsertOperation(position, userId, character);
                    session.getCrdt().applyOperation(op);
                } else if ("delete".equals(type)) {
                    int position = data.get("position").getAsInt();
                    if (position < 0 || position > session.getCrdt().getLength()) {
                        System.err.println("Invalid insert position: " + position);
                        return;
                    }
                    CRDTOperation op = session.getCrdt().createDeleteOperation(position, userId);
                    session.getCrdt().applyOperation(op);
                }

                // Broadcast update
                JsonObject update = new JsonObject();
                update.addProperty("type", "DOCUMENT_UPDATE");
                update.addProperty("sessionId", sessionId);
                update.addProperty("content", session.getCrdt().getContent());
                update.addProperty("userId", userId);

                messagingTemplate.convertAndSend("/topic/session." + sessionId, update.toString());
            }
        } catch (Exception e) {
            //System.err.println("Error processing operation: " + e.getMessage());
        }
    }
}