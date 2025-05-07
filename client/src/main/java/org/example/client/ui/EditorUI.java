package org.example.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.util.Duration;
import org.example.client.api.DocumentService;
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
    private boolean isProcessingRemoteUpdate = false;
    private Button backButton;
    public EditorUI(Stage primaryStage, ServerConnection connection, DocumentService documentService) {
        this.primaryStage = primaryStage;
        this.connection = connection;
        this.documentService = documentService;
        connection.connectInternal(WS_BASE_URL, null, null);
        this.userId = "user_" + System.currentTimeMillis();
        showInitialScreen();
        // Set up close handler
        primaryStage.setOnCloseRequest(event -> {
            stop();
        });
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
    private void handleServerUpdate(String message) {
        Platform.runLater(() -> {
            try {
                JsonObject update = JsonParser.parseString(message).getAsJsonObject();
                String type = update.get("type").getAsString();

                if ("DOCUMENT_UPDATE".equals(type)) {

                    String senderId = update.get("userId").getAsString();
                    // Only update if change came from another user
                    if (!senderId.equals(userId)) {
                        isProcessingRemoteUpdate = true;
                        String content = update.get("content").getAsString();
                        editor.setInitialContent(content);
                        isProcessingRemoteUpdate = false;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing update: " + e.getMessage());
            }
        });
    }
    private void handlePresenceUpdate(String message) {
        Platform.runLater(() -> {
            try {
                JsonObject update = JsonParser.parseString(message).getAsJsonObject();
                String type = update.get("type").getAsString();

                if ("USER_PRESENCE".equals(type)) {
                    JsonArray activeUsers = update.get("activeUsers").getAsJsonArray();
                    updateUserList(activeUsers);
                    System.out.println("Handle Presence Update");
                }
            } catch (Exception e) {
                System.err.println("Error processing presence update: " + e.getMessage());
            }
        });
    }

    private void setupTextListeners() {
        TextArea textArea = editor.getTextArea();

        // Existing text change listener
        textArea.textProperty().addListener((obs, oldText, newText) -> {
            if (isUndoRedoOperation || isProcessingRemoteUpdate) return;

            textArea.caretPositionProperty().addListener((obs1, oldVal, newVal) -> {
                sendCurrentPosition();
            });
            // Calculate position difference
            int diffPos = 0;
            while (diffPos < oldText.length() &&
                    diffPos < newText.length() &&
                    oldText.charAt(diffPos) == newText.charAt(diffPos)) {
                diffPos++;
            }

            // Handle insert/delete via WebSocket
            if (newText.length() > oldText.length()) {
                // Check if this is a multi-character insert (likely paste)
                if (newText.length() - oldText.length() > 1) {
                    // Handle paste operation
                    handlePasteOperation(oldText, newText, diffPos);
                } else {
                    // Single character insert
                    JsonObject data = new JsonObject();
                    data.addProperty("position", diffPos);
                    data.addProperty("character", newText.charAt(diffPos));
                    connection.sendOperation("insert", data, currentDocId, userId);
                }
            }
            else if (newText.length() < oldText.length()) {
                JsonObject data = new JsonObject();
                data.addProperty("position", diffPos);
                connection.sendOperation("delete", data, currentDocId, userId);
            }
        });

        // Add paste event handler to detect paste operations
        textArea.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.V) {
                // Store the current text before paste occurs
                String textBeforePaste = textArea.getText();
                int caretPosition = textArea.getCaretPosition();

                // Use Platform.runLater to check the text after paste has occurred
                Platform.runLater(() -> {
                    String textAfterPaste = textArea.getText();
                    if (textAfterPaste.length() - textBeforePaste.length() > 1) {
                        // This was a paste operation
                        handlePasteOperation(textBeforePaste, textAfterPaste, caretPosition);
                    }
                });
            }
        });
    }

    private void handlePasteOperation(String oldText, String newText, int pastePosition) {
        // Calculate the pasted content
        System.out.println("paste");
        String pastedContent = newText.substring(pastePosition, pastePosition + (newText.length() - oldText.length()));

        // Send insert operations for each character in the pasted content
        for (int i = 0; i < pastedContent.length(); i++) {
            char c = pastedContent.charAt(i);
            try {
                //ensure they are send in the right order
                Thread.sleep(10); // 10 milliseconds delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore interrupt status
                // handle interruption if needed
                break;}
            JsonObject data = new JsonObject();
            data.addProperty("position", pastePosition);
            data.addProperty("character", c);
            connection.sendOperation("insert", data, currentDocId, userId);
        }
    }
    private void importDocument() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            new Thread(() -> {
                try {
                    // 1. First create a new empty document
                    System.out.println("Creating new document for import...");
                    JsonObject createResponse = documentService.createDocument(userId);
                    this.currentDocId = createResponse.get("documentId").getAsString();
                    this.isEditor = true;

                    // 2. Read the file content
                    System.out.println("Reading file content...");
                    String fileContent = new String(Files.readAllBytes(file.toPath()));

                    // 3. Show the UI with empty content first
                    Platform.runLater(() -> {
                        showEditorUI("");
                        showShareCodes();
                        //start listining for updates
                        processServerMessages();

                        // 4. After UI is ready, paste the entire content
                        // This will trigger the normal paste handling we implemented earlier
                        editor.getTextArea().setText(fileContent);

                        // 5. Save initial state to undo stack
                        saveStateToUndoStack(fileContent);
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
        JsonObject json = JsonParser.parseString(message).getAsJsonObject();
        String type = json.get("type").getAsString();
        String senderUserId = json.get("userId").getAsString();

        // Ignore our own updates (prevent echo)
        if (senderUserId.equals(userId)) return;

        switch (type) {
            case "DOCUMENT_UPDATE":
                editor.setInitialContent(json.get("content").getAsString());
                break;
            case "USER_PRESENCE":
                // This will contain active users and their positions
                JsonElement usersElem = json.get("activeUsers");
                if (usersElem != null && usersElem.isJsonArray()) {
                    updateUserList(usersElem.getAsJsonArray());
                }
                else{System.out.println("Empty");}
                break;
        }
    }
    private void showEditorUI(String initialContent) {
        BorderPane root = new BorderPane();

        // Top Bar with buttons
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);


        //this.backButton = new Button("← Back");
        //backButton.setStyle("-fx-font-size: 14px;");
        //backButton.setOnAction(e -> returnToInitialScreen());


        Button shareButton = new Button("Share");
        shareButton.setStyle("-fx-font-size: 14px;");
        shareButton.setOnAction(e -> showShareCodes());

        // Undo/Redo buttons
        Button undoButton = new Button("Undo (Ctrl+Z)");
        undoButton.setOnAction(e -> undo());

        Button redoButton = new Button("Redo (Ctrl+Y)");
        redoButton.setOnAction(e -> redo());

        topBar.getChildren().addAll(
        //        backButton,
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
        Platform.runLater(() -> {
            sendCurrentPosition();
        });

        // Start periodic position updates (every 500ms)
        Timeline positionUpdater = new Timeline(
                new KeyFrame(Duration.millis(500), e -> sendCurrentPosition())
        );
        positionUpdater.setCycleCount(Timeline.INDEFINITE);
        positionUpdater.play();
        primaryStage.setScene(new Scene(root, 800, 600));
        setupTextListeners();
        connection.subscribeToSessionUpdates(currentDocId, this::handleServerUpdate);
        connection.subscribeToPresenceUpdates(currentDocId, this::handlePresenceUpdate);

    }
    private void updateUserList(JsonArray users) {
        VBox userList = (VBox) ((BorderPane) primaryStage.getScene().getRoot()).getRight();
        userList.getChildren().clear();

        Label usersLabel = new Label("Active Users");
        usersLabel.setStyle("-fx-font-weight: bold;");
        userList.getChildren().add(usersLabel);

        for (JsonElement userElement : users) {
            if (!userElement.isJsonObject()) continue;

            JsonObject user = userElement.getAsJsonObject();

            // Safe field access with defaults
            String userId = user.has("userId") ? user.get("userId").getAsString() : "unknown";
            boolean isEditor = user.has("isEditor") && user.get("isEditor").getAsBoolean();

            // Handle potentially missing lineNumber
            int lineNumber = 0; // Default value
            if (isEditor && user.has("lineNumber")) {
                try {
                    lineNumber = user.get("lineNumber").getAsInt();
                } catch (Exception e) {
                    System.err.println("Invalid lineNumber for user " + userId);
                }
            }

            String userType = isEditor ? " (Editor)" : " (Viewer)";
            String lineInfo = isEditor ? " - Line " + (lineNumber + 1) : "";
            Label userLabel = new Label(userId + userType + lineInfo);
            userLabel.setStyle("-fx-text-fill: " + getColorForUser(userId) + ";");
            userList.getChildren().add(userLabel);
        }
    }
    private void sendCurrentPosition() {
        if (currentDocId == null) return;

        TextArea textArea = editor.getTextArea();
        int caretPosition = textArea.getCaretPosition();
        String text = textArea.getText();

        // Calculate line number
        int lineNumber = 0;
        int lastNewline = 0;
        while (lastNewline != -1 && lastNewline < caretPosition) {
            lastNewline = text.indexOf('\n', lastNewline);
            if (lastNewline != -1 && lastNewline < caretPosition) {
                lineNumber++;
                lastNewline++;
            }
        }

        JsonObject positionUpdate = new JsonObject();
        positionUpdate.addProperty("type", "POSITION_UPDATE");
        positionUpdate.addProperty("documentId", currentDocId);
        positionUpdate.addProperty("userId", userId);
        positionUpdate.addProperty("isEditor", isEditor);
        positionUpdate.addProperty("lineNumber", lineNumber);

        connection.sendPositionUpdate(currentDocId, userId, isEditor, lineNumber);
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

        // Generate codes in the format: view-doc_[id] and edit-doc_[id]
        String baseId = currentDocId.replace("doc_", ""); // Remove "doc_" prefix if present
        String viewerCode = "view-doc_" + baseId.substring(0, baseId.length() - 3);
        String editorCode = "edit-doc_" + baseId.substring(0, baseId.length() - 3);

        shareStage = new Stage();
        VBox root = new VBox(20,
                new Label("Share This Document") {{
                    setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
                }},
                new Label("Viewer Code (Read-Only)"),
                new TextField(viewerCode) {{
                    setEditable(false);
                }},
                new Label("Editor Code (Full Access)"),
                new TextField(editorCode) {{
                    setEditable(false);
                }},
                new Button("Copy All") {{
                    setOnAction(e -> {
                        String content = "Viewer Code: " + viewerCode + "\nEditor Code: " + editorCode;
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent clipboardContent = new ClipboardContent();
                        clipboardContent.putString(content);
                        clipboard.setContent(clipboardContent);
                        showAlert("Copied", "All codes copied to clipboard!");
                    });
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
        String baseId = currentDocId.replace("doc_", ""); // Remove "doc_" prefix if present
        String viewerCode = "view-doc_" + baseId.substring(0, baseId.length() - 3);
        String editorCode = "edit-doc_" + baseId.substring(0, baseId.length() - 3);
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
        connection.sendLeaveMessage(currentDocId, userId);
        running = false;
    }
}