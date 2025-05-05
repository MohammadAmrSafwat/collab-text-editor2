package org.example.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.client.api.DocumentService;
import org.example.client.api.OperationService;
import org.example.client.network.ServerConnection;
import org.example.client.ui.components.TextAreaWithCursors;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.io.File;
import java.nio.file.Files;

public class EditorUI {
    private static final String API_BASE_URL = "http://localhost:8080/api";
    private static final String WS_BASE_URL = "ws://localhost:8080";
    private Stage shareStage;
    private final Stage primaryStage;
    private final ServerConnection connection;
    private final String userId;
    private String currentDocId;
    private boolean isEditor;
    private TextAreaWithCursors editor;
    private volatile boolean running = true;
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private boolean isUndoRedoOperation = false;
    private final DocumentService documentService;
    private final OperationService operationService;
    private Button backButton;
    public EditorUI(Stage primaryStage, ServerConnection connection, DocumentService documentService, OperationService operationService) {
        this.primaryStage = primaryStage;
        this.connection = connection;
        this.documentService = documentService;
        this.operationService = operationService;
        connection.connect(WS_BASE_URL);
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

    private void setupTextListeners() {
        editor.getTextArea().textProperty().addListener((obs, oldText, newText) -> {
            if (isUndoRedoOperation) return;

            // Calculate difference
            int diffPos = 0;
            while (diffPos < oldText.length() &&
                    diffPos < newText.length() &&
                    oldText.charAt(diffPos) == newText.charAt(diffPos)) {
                diffPos++;
            }

            // Handle inserts
            if (newText.length() > oldText.length()) {
                char insertedChar = newText.charAt(diffPos);
                try {
                    operationService.insertOperation(currentDocId, userId, diffPos, insertedChar);
                } catch (Exception e) {
                    showAlert("Error", "Failed to sync insert: " + e.getMessage());
                }
            }
            // Handle deletes
            else if (newText.length() < oldText.length()) {
                try {
                    operationService.deleteOperation(currentDocId, userId, diffPos);
                } catch (Exception e) {
                    showAlert("Error", "Failed to sync delete: " + e.getMessage());
                }
            }
        });
    }

    private void importDocument() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            new Thread(() -> {
                try {
                    System.out.println("Attempting to import file: " + file.getPath());
                    JsonObject response = documentService.importDocument(userId, file);
                    System.out.println("Import response: " + response);

                    this.currentDocId = response.get("documentId").getAsString();
                    this.isEditor = response.get("isEditor").getAsBoolean();
                    String content = response.get("content").getAsString();

                    Platform.runLater(() -> {
                        showEditorUI(content);
                        if (isEditor) {
                            showShareCodes();
                        }
                        processServerMessages();
                    });
                } catch (Exception e) {
                    System.err.println("Import failed: " + e.getMessage());
                    e.printStackTrace();
                    Platform.runLater(() ->
                            showAlert("Import Error", "Failed to import file: " + e.getMessage())
                    );
                }
            }).start();
        }
    }

    public void createNewDocument() {
        new Thread(() -> {
            try {

                JsonObject response = documentService.createDocument(userId);  // Use the stored service
                this.currentDocId = response.get("documentId").getAsString();
                this.isEditor = true;
                Platform.runLater(() -> {
                    showEditorUI("");
                    showShareCodes();
                    processServerMessages();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Connection Error");
                    alert.setHeaderText("Failed to connect to server");
                    alert.setContentText("Please make sure the server is running and try again.");
                    alert.showAndWait();
                });
            }
        }).start();
    }

    public void joinSession(String sessionCode) {
        if (sessionCode == null || sessionCode.trim().isEmpty()) {
            showAlert("Error", "Please enter a session code");
            return;
        }

        new Thread(() -> {
            try {
                // Call DocumentService to join session
                JsonObject response = documentService.joinSession(userId, sessionCode);

                // Update UI state from response
                this.currentDocId = response.get("documentId").getAsString();
                this.isEditor = response.get("isEditor").getAsBoolean();
                String initialContent = response.get("content").getAsString();

                // Get share codes if available (only for editors)
                String viewCode = response.has("viewCode") ?
                        response.get("viewCode").getAsString() : currentDocId + "-view";
                String editCode = response.has("editCode") ?
                        response.get("editCode").getAsString() : currentDocId + "-edit";

                Platform.runLater(() -> {
                    // Show editor with existing content
                    showEditorUI(initialContent);

                    // Show share codes if editor
                    if (isEditor) {
                        //showShareCodes(viewCode, editCode);
                    } else {
                        editor.getTextArea().setEditable(false);
                        showAlert("Info", "You've joined as a viewer (read-only)");
                    }

                    // Start listening for updates
                    processServerMessages();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Connection Error");
                    alert.setHeaderText("Failed to join session");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    private void processServerMessages() {
        new Thread(() -> {
            while (running) {
                try {
                    String message = connection.receiveMessage();
                    if (message != null) {
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

        // Top Bar with buttons
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);


        this.backButton = new Button("← Back");
        backButton.setStyle("-fx-font-size: 14px;");
        backButton.setOnAction(e -> returnToInitialScreen());


        Button shareButton = new Button("Share");
        shareButton.setStyle("-fx-font-size: 14px;");
        shareButton.setOnAction(e -> showShareCodes());

        // Undo/Redo buttons
        Button undoButton = new Button("Undo (Ctrl+Z)");
        undoButton.setOnAction(e -> undo());

        Button redoButton = new Button("Redo (Ctrl+Y)");
        redoButton.setOnAction(e -> redo());

        topBar.getChildren().addAll(
                backButton,
                undoButton,
                redoButton,
                new Label("Document: " + currentDocId.substring(0, Math.min(8, currentDocId.length()))),
                new Button("Export") {{
                    setOnAction(e -> exportDocument());
                }},
                shareButton
        );

        // Editor Area

        editor = new TextAreaWithCursors(connection, currentDocId, userId, isEditor);
        editor.setInitialContent(initialContent);

        // Save initial state
        saveStateToUndoStack(editor.getTextArea().getText());

        // Set up listeners
        setupUndoRedoHandlers();
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
        setupTextListeners();

    }
    private void updateUserList(JsonArray users) {
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
    private void returnToInitialScreen() {
        // Clean up resources
        if (connection != null) {
            connection.disconnect();
        }

        // Reset document state
        currentDocId = null;
        isEditor = false;
        editor = null;

        // Show initial screen
        showInitialScreen();
    }

    private String getColorForUser(String userId) {
        int hash = userId.hashCode();
        return String.format("#%06x", hash & 0xFFFFFF);
    }

    private void showShareCodes() {
        // Close existing share window if open
        if (shareStage != null) {
            shareStage.close();
        }

        shareStage = new Stage();
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
                new Button("Copy All") {{
                    setOnAction(e -> copyAllCodesToClipboard());
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



    private void copyAllCodesToClipboard() {
        String viewerCode = currentDocId + "-view";
        String editorCode = currentDocId + "-edit";
        String content = "Viewer Code: " + viewerCode + "\nEditor Code: " + editorCode;

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(content);
        clipboard.setContent(clipboardContent);

        showAlert("Copied", "All codes copied to clipboard!");
    }

    private void setupUndoRedoHandlers() {
        TextArea textArea = editor.getTextArea();

        // Track changes for undo
        textArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUndoRedoOperation) {
                saveStateToUndoStack(oldVal);
                redoStack.clear(); // Clear redo stack on new changes
            }
        });

        // Add keyboard shortcuts
        textArea.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown()) {
                if (event.getCode() == KeyCode.Z) {
                    undo();
                    event.consume();
                } else if (event.getCode() == KeyCode.Y) {
                    redo();
                    event.consume();
                }
            }
        });
    }

    private void saveStateToUndoStack(String text) {
        if (text != null && !text.isEmpty()) {
            undoStack.push(text);
        }
    }

    private void undo() {
        if (undoStack.size() > 1) { // Need at least 2 states (current + previous)
            String current = undoStack.pop();
            redoStack.push(current);
            restoreState(undoStack.peek());
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            String state = redoStack.pop();
            undoStack.push(state);
            restoreState(state);
        }
    }

    private void restoreState(String text) {
        if (text != null) {
            isUndoRedoOperation = true;
            editor.getTextArea().setText(text);
            isUndoRedoOperation = false;
        }
    }

    public void stop() {
        running = false;
        connection.disconnect();
    }
}