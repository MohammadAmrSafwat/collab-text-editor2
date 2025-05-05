package org.example.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.client.network.ServerConnection;
import org.example.client.ui.components.TextAreaWithCursors;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.net.http.HttpHeaders;
import java.nio.file.Files;

public class EditorUI {
    private final Stage primaryStage;
    private final ServerConnection connection;
    private final String userId;
    private String currentDocId;
    private boolean isEditor;
    private TextAreaWithCursors editor;

    public EditorUI(Stage primaryStage, ServerConnection connection) {
        this.primaryStage = primaryStage;
        this.connection = connection;
        connection.connect("ws://localhost:8080");
        this.userId = "user_" + System.currentTimeMillis();
        showInitialScreen();
    }

    private void showInitialScreen() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label title = new Label("Collaborative Text Editor");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Button newDocBtn = new Button("New Document");
        Button importBtn = new Button("Import File");
        Button joinBtn = new Button("Join Session");

        TextField sessionCodeField = new TextField();
        sessionCodeField.setPromptText("Enter session code");
        sessionCodeField.setMaxWidth(200);

        newDocBtn.setOnAction(e -> createNewDocument());
        importBtn.setOnAction(e -> importDocument());
        joinBtn.setOnAction(e -> joinSession(sessionCodeField.getText()));

        HBox joinBox = new HBox(10, sessionCodeField, joinBtn);
        joinBox.setAlignment(Pos.CENTER);

        root.getChildren().addAll(title, newDocBtn, importBtn, joinBox);

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Collaborative Editor");
        primaryStage.show();
    }

    private void createNewDocument() {
        // Simulate server response for demo
        this.currentDocId = generateRandomCode(1);
        createSession(currentDocId);
        this.isEditor = true;
        showEditorUI("");
        showShareCodes();
    }

    private void importDocument() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                String content = new String(Files.readAllBytes(file.toPath()));
                this.currentDocId = generateRandomCode(1);
                createSession();
                this.isEditor = true;
                showEditorUI(content);
                showShareCodes();
            } catch (Exception e) {
                showAlert("Error", "Failed to import file: " + e.getMessage());
            }
        }
    }
    public JsonObject createNewDocument() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        JsonObject request = new JsonObject();
        request.addProperty("userId", this.userId);
        request.addProperty("content", ""); // Empty content for new document

        HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:8080/api/documents",
                entity,
                String.class
        );

        JsonObject responseJson = JsonParser.parseString(response.getBody()).getAsJsonObject();
        this.currentDocId = responseJson.get("documentId").getAsString();
        return responseJson;
    }

    private String generateRandomCode(int lenght) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"; // Letters AND numbers
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lenght; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    public JsonObject joinSession(String sessionCode) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        JsonObject request = new JsonObject();
        request.addProperty("sessionCode", sessionCode);
        request.addProperty("userId", this.userId);

        HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:8080/api/sessions/join",
                entity,
                String.class
        );

        return JsonParser.parseString(response.getBody()).getAsJsonObject();
    }
    private void createSession() {
        currentDocId;
        userid;
    }
    private void processServerMessages() {
        new Thread(() -> {
            while (true) {
                try {
                    String message = connection.receiveMessage();
                    if (message != null) {  // Additional null check
                        Platform.runLater(() -> handleServerMessage(message));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    private void handleServerMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            System.out.println("Received empty message, ignoring");
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.get("type").getAsString();

            switch (type) {
                case "DOCUMENT_UPDATE":
                    editor.setInitialContent(json.get("content").getAsString());
                    break;

                case "CURSOR_UPDATE":
                    editor.updateRemoteCursor(
                            json.get("userId").getAsString(),
                            json.get("position").getAsInt()
                    );
                    break;

                case "USER_LIST":
                    updateUserList(json.get("users").getAsJsonArray());
                    break;

                default:
                    System.out.println("Unknown message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            System.err.println("Original message: " + message);
        }
    }
    private void showEditorUI(String initialContent) {
        BorderPane root = new BorderPane();

        // Top Bar
        HBox topBar = new HBox(10,
                new Label("Document: " + currentDocId.substring(0, Math.min(8, currentDocId.length()))),
                new Button("Export") {{
                    setOnAction(e -> exportDocument());
                }}
        );
        topBar.setPadding(new Insets(10));

        // Editor Area
        editor = new TextAreaWithCursors(connection, currentDocId, userId, isEditor);
        editor.setInitialContent(initialContent);

        // User Presence
        VBox userList = new VBox(10);
        userList.setPadding(new Insets(10));
        userList.setStyle("-fx-background-color: #f0f0f0;");

        Label usersLabel = new Label("Active Users");
        usersLabel.setStyle("-fx-font-weight: bold;");
        userList.getChildren().add(usersLabel);

        root.setTop(topBar);
        root.setCenter(editor);
        root.setRight(userList);

        primaryStage.setScene(new Scene(root, 800, 600));
    }

    private void updateUserList(com.google.gson.JsonArray users) {
        VBox userList = (VBox) ((BorderPane) primaryStage.getScene().getRoot()).getRight();
        userList.getChildren().clear();
        userList.getChildren().add(new Label("Active Users") {{
            setStyle("-fx-font-weight: bold;");
        }});

        users.forEach(user -> {
            String userId = user.getAsString();
            userList.getChildren().add(new Label(userId) {{
                setStyle("-fx-text-fill: " + getColorForUser(userId) + ";");
            }});
        });
    }
    //using the user id to get color
    private String getColorForUser(String userId) {
        int hash = userId.hashCode();
        return String.format("#%06x", hash & 0xFFFFFF);
    }

    private void showShareCodes() {
        Stage shareStage = new Stage();
        VBox root = new VBox(20,
                new Label("Share This Document") {{
                    setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
                }},
                new Label("Viewer Code (Read-Only)"),
                new TextField(currentDocId + "-view") {{
                    setEditable(false);
                }},
                new Label("Editor Code (Full Access)"),
                new TextField(currentDocId + "-edit") {{
                    setEditable(false);
                }},
                new Button("Close") {{
                    setOnAction(e -> shareStage.close());
                }}
        );
        root.setPadding(new Insets(20));
        shareStage.setScene(new Scene(root, 300, 250));
        shareStage.setTitle("Share Codes");
        shareStage.show();
    }

    private void exportDocument() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try {
                Files.write(file.toPath(), editor.getTextArea().getText().getBytes());
                showAlert("Success", "Document exported successfully");
            } catch (Exception e) {
                showAlert("Error", "Failed to export: " + e.getMessage());
            }
        }
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.INFORMATION, message) {{
                    setTitle(title);
                    setHeaderText(null);
                    showAndWait();
                }}
        );
    }
}