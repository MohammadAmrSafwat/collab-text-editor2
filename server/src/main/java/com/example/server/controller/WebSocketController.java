package com.example.server.controller;

import com.example.server.model.*;
import com.example.server.service.CollaborationService;
import com.example.server.service.crdt.CRDTOperation;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    private final CollaborationService collaborationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Gson gson = new Gson();

    @Autowired
    public WebSocketController(CollaborationService collaborationService,
                               SimpMessagingTemplate messagingTemplate) {
        this.collaborationService = collaborationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/session/{sessionId}/join")
    public void handleJoin(@DestinationVariable String sessionId, JoinRequest request) {
        Session session = collaborationService.joinSession(
                sessionId,
                request.getUserId(),
                request.getCode(),
                request.isEditor()
        );

        messagingTemplate.convertAndSendToUser(
                request.getUserId(),
                "/queue/init",
                new SessionState(session.getCrdt().getContent(), sessionId)
        );
    }

    @MessageMapping("/session/{sessionId}/operation")
    @SendTo("/topic/session/{sessionId}/updates")
    public OperationResponse handleOperation(
            @DestinationVariable String sessionId,
            ClientOperation operation
    ) {
        CRDTOperation op = collaborationService.processOperation(
                sessionId,
                operation.getUserId(),
                operation
        );

        return new OperationResponse(
                op,
                collaborationService.getSession(sessionId).getCrdt().getContent()
        );
    }
}