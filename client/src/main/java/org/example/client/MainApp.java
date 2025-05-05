package org.example.client;

import javafx.application.Application;
import javafx.stage.Stage;
import org.example.client.api.DocumentService;
import org.example.client.api.OperationService;
import org.example.client.network.ServerConnection;
import org.example.client.ui.EditorUI;

public class MainApp extends Application {
    private static final String API_BASE_URL = "http://localhost:8080/api";
    private static final String WS_BASE_URL = "ws://localhost:8080";

    @Override
    public void start(Stage primaryStage) {
        try {
            ServerConnection connection = new ServerConnection();
            DocumentService documentService = new DocumentService(API_BASE_URL);
            OperationService OperationService = new OperationService(API_BASE_URL);
            new EditorUI(primaryStage, connection, documentService, OperationService);
        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}