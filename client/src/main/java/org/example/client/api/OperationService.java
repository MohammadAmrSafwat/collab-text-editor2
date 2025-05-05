package org.example.client.api;

import com.google.gson.JsonObject;
import org.example.client.network.ServerConnection;

public class OperationService {
    private final ServerConnection connection;

    public OperationService(ServerConnection connection) {
        this.connection = connection;
    }

    public void insertOperation(String docId, String userId, int position, char character) {
        JsonObject data = new JsonObject();
        data.addProperty("position", position);
        data.addProperty("character", character);
        connection.sendOperation("insert", data, docId, userId);
    }

    public void deleteOperation(String docId, String userId, int position) {
        JsonObject data = new JsonObject();
        data.addProperty("position", position);
        connection.sendOperation("delete", data, docId, userId);
    }
}