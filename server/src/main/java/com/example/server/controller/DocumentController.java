package com.example.server.controller;


import com.example.server.model.Session;
import com.example.server.service.CollaborationService;
import com.example.server.service.crdt.CRDTOperation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.google.gson.JsonObject;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final CollaborationService collaborationService;

    public DocumentController(CollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createDocument(@RequestBody Map<String, String> request) {
        try {
            // More flexible request handling using Map
            if (request == null || !request.containsKey("userId")) {
                return ResponseEntity.badRequest().body("{\"error\":\"userId is required\"}");
            }

            String userId = request.get("userId");
            String content = request.getOrDefault("content", "");

            // Rest of your implementation...
            String documentId = "doc_" + System.currentTimeMillis();
            Session session = new Session(documentId);

            // Initialize CRDT if content exists
            if (!content.isEmpty()) {
                for (int i = 0; i < content.length(); i++) {
                    CRDTOperation op = session.getCrdt().createInsertOperation(i, userId, content.charAt(i));
                    session.getCrdt().applyOperation(op);
                }
            }

            // Store session (add this method to CollaborationService)
            collaborationService.addSession(session);

            // Build response
            JsonObject response = new JsonObject();
            response.addProperty("documentId", documentId);
            response.addProperty("viewCode", session.getViewerCode());
            response.addProperty("editCode", session.getEditorCode());
            response.addProperty("content", content);
            response.addProperty("isEditor", true);

            return ResponseEntity.ok(response.toString());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Failed to create document: " + e.getMessage() + "\"}");
        }
    }
}