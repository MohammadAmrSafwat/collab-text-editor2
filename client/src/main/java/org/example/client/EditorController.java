package org.example.client;

import org.example.client.crdt.SyncEngine;
import org.example.client.network.MessageHandler;
import org.example.client.network.ServerConnection;
import org.example.client.model.ClientEditorOperation;
import org.example.client.ui.components.TextAreaWithCursors;
import javafx.fxml.FXML;

import java.util.List;

public class EditorController {
    @FXML
    private TextAreaWithCursors textAreaWithCursors; // Changed from TextArea

    private ServerConnection connection;
    private String currentSessionId;
    private String userId;
    private SyncEngine syncEngine;
    private boolean isApplyingRemoteChange = false;

    public void initialize(TextAreaWithCursors textAreaWithCursors, String serverUrl,
                           String sessionId, boolean isEditor) {
        this.textAreaWithCursors = textAreaWithCursors;
        this.currentSessionId = sessionId;
        this.userId = "user-" + System.currentTimeMillis();
        this.syncEngine = new SyncEngine(textAreaWithCursors.getTextArea().getText());

        try {
            this.connection = new ServerConnection();
            connection.connect(serverUrl, sessionId, userId);
            setupListeners();
            startMessageProcessingThread();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupListeners() {
        textAreaWithCursors.getTextArea().textProperty().addListener((obs, oldVal, newVal) -> {
            if (!isApplyingRemoteChange) {
                List<ClientEditorOperation> operations = syncEngine.calculateDiff(newVal);
                operations.forEach(op -> {
                   // connection.sendOperation("OPERATION", currentSessionId, userId, op);
                });
            }
        });
    }

    private void startMessageProcessingThread() {
        new Thread(() -> {
            while (true) {
                try {
                    String message = connection.receiveMessage();
                    javafx.application.Platform.runLater(() -> {
                        handleServerMessage(message);
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    private void handleServerMessage(String message) {
        MessageHandler.handle(message, this);
    }

    public void updateContent(ClientEditorOperation operation) {
        isApplyingRemoteChange = true;
        String newText = syncEngine.applyOperation(
                textAreaWithCursors.getTextArea().getText(),
                operation
        );

        // Use the TextAreaWithCursors methods instead of direct TextArea access
        if (operation.getType().equals("INSERT")) {
            textAreaWithCursors.insertText(operation.getPosition(), operation.getText());
        } else if (operation.getType().equals("DELETE")) {
            textAreaWithCursors.deleteText(
                    operation.getPosition(),
                    operation.getPosition() + operation.getLength()
            );
        }

        syncEngine = new SyncEngine(newText);
        isApplyingRemoteChange = false;
    }

    public void showRemoteCursor(String userId, int position) {
        textAreaWithCursors.updateRemoteCursor(userId, position);
    }

    public void initializeSession(String content, String sessionId) {
        this.currentSessionId = sessionId;
        isApplyingRemoteChange = true;
        textAreaWithCursors.setInitialContent(content);
        syncEngine = new SyncEngine(content);
        isApplyingRemoteChange = false;
    }

    // For FXML initialization
    @FXML
    public void initialize() {
        if (textAreaWithCursors != null) {
            this.userId = "user-" + System.currentTimeMillis();
            this.syncEngine = new SyncEngine(textAreaWithCursors.getTextArea().getText());
        }
    }
}