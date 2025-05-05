package com.example.server.controller;

import com.example.server.model.Session;
import com.example.server.service.CollaborationService;
import com.example.server.service.crdt.CRDTOperation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/operations")
public class OperationController {
    private final CollaborationService collaborationService;

    public OperationController(CollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    @PostMapping
    public ResponseEntity<String> handleOperation(@RequestBody Map<String, String> request) {
        try {
            String docId = request.get("docId");
            String userId = request.get("userId");
            String type = request.get("type");

            Session session = collaborationService.getSession(docId);
            if (session == null) {
                return ResponseEntity.notFound().build();
            }

            if ("insert".equals(type)) {
                int position = Integer.parseInt(request.get("position"));
                char character = request.get("character").charAt(0);
                CRDTOperation op = session.getCrdt().createInsertOperation(position, userId, character);
                session.getCrdt().applyOperation(op);
            }
            else if ("delete".equals(type)) {
                int position = Integer.parseInt(request.get("position"));
                CRDTOperation op = session.getCrdt().createDeleteOperation(position, userId);
                session.getCrdt().applyOperation(op);
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}