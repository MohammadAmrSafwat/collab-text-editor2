package org.example.client.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class DocumentService {
    private final RestTemplate restTemplate;
    private final String apiBaseUrl;

    public DocumentService(String apiBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.apiBaseUrl = apiBaseUrl;
        // Configure timeout settings
        // ((HttpComponentsClientHttpRequestFactory)restTemplate.getRequestFactory()).setConnectTimeout(5000);
    }

    public JsonObject createDocument(String userId) throws Exception {
        try {
            JsonObject request = new JsonObject();
            request.addProperty("userId", userId);
            request.addProperty("content", "");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    apiBaseUrl + "/documents",
                    entity,
                    String.class
            );

            return JsonParser.parseString(response.getBody()).getAsJsonObject();
        } catch (RestClientException e) {
            throw new Exception("Failed to connect to server. Please check if the server is running.", e);
        }
    }

    // ... other methods ...
}