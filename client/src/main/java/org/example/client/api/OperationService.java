package org.example.client.api;

import com.google.gson.JsonObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

public class OperationService {
    private final RestTemplate restTemplate;
    private final String apiBaseUrl;

    public OperationService(String apiBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.apiBaseUrl = apiBaseUrl;
    }

    public void insertOperation(String docId, String userId, int position, char character) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("docId", docId);
        request.addProperty("userId", userId);
        request.addProperty("position", position);
        request.addProperty("character", character);
        request.addProperty("type", "insert");

        sendOperation(request);
    }

    public void deleteOperation(String docId, String userId, int position) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("docId", docId);
        request.addProperty("userId", userId);
        request.addProperty("position", position);
        request.addProperty("type", "delete");

        sendOperation(request);
    }

    private void sendOperation(JsonObject operation) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(operation.toString(), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                apiBaseUrl + "/operations",
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new Exception("Operation failed: " + response.getBody());
        }
    }
}