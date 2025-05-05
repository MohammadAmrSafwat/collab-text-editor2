package org.example.client.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.client.model.ClientEditorOperation;
import org.example.client.EditorController;
import com.google.gson.Gson;

public class MessageHandler {
    private static final JsonParser parser = new JsonParser();
    private static final Gson gson = new Gson();

    public static void handle(String message, EditorController controller) {
        JsonObject json = parser.parse(message).getAsJsonObject();
        String type = json.get("type").getAsString();

        switch (type) {
            case "DOCUMENT_UPDATE":
                ClientEditorOperation operation = gson.fromJson(
                        json.get("operation").getAsJsonObject(),
                        ClientEditorOperation.class
                );
                controller.updateContent(operation);
                break;

            case "CURSOR_UPDATE":
                controller.showRemoteCursor(
                        json.get("userId").getAsString(),
                        json.get("position").getAsInt()
                );
                break;

            case "SESSION_STATE":
                controller.initializeSession(
                        json.get("content").getAsString(),
                        json.get("sessionId").getAsString()
                );
                break;
        }
    }
}