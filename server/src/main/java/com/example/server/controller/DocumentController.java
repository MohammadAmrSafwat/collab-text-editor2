package com.example.server.controller;


import org.springframework.web.bind.annotation.*;
import com.google.gson.JsonObject;

@RestController
@RequestMapping("/api")
public class DocumentController {

    @PostMapping("/documents")
    public String createDocument(@RequestBody JsonObject request) {
        // Simple response for testing
        JsonObject response = new JsonObject();
        response.addProperty("documentId", "doc_" + System.currentTimeMillis());
        return response.toString();
    }
}