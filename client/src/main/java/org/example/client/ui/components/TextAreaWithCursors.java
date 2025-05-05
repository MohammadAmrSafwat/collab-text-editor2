package org.example.client.ui.components;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.example.client.network.ServerConnection;

import java.util.HashMap;
import java.util.Map;

public class TextAreaWithCursors extends Pane {
    private final TextArea textArea;
    private final Map<String, Line> remoteCursors = new HashMap<>();
    private final Map<String, Color> userColors = new HashMap<>();
    private final ServerConnection connection;
    private final String currentDocId;
    private final String userId;
    private final boolean isEditor;

    public TextAreaWithCursors(ServerConnection connection, String currentDocId,
                               String userId, boolean isEditor) {
        this.connection = connection;
        this.currentDocId = currentDocId;
        this.userId = userId;
        this.isEditor = isEditor;

        // Configure main text area
        textArea = new TextArea();
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: monospace;");
        textArea.setEditable(isEditor);

        // Make it fill the available space
        textArea.prefWidthProperty().bind(this.widthProperty());
        textArea.prefHeightProperty().bind(this.heightProperty());

        this.getChildren().add(textArea);

        // Setup listeners
        setupCursorTracking();
    }

    public void setInitialContent(String content) {
        textArea.setText(content);
    }

    public void insertText(int position, String text) {
        Platform.runLater(() -> {
            int caretPos = textArea.getCaretPosition();
            textArea.insertText(position, text);
            textArea.positionCaret(caretPos);
        });
    }

    public void deleteText(int start, int end) {
        Platform.runLater(() -> {
            int caretPos = textArea.getCaretPosition();
            textArea.deleteText(start, end);
            textArea.positionCaret(caretPos);
        });
    }

    private void setupCursorTracking() {
        textArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (isEditor) {
                JsonObject cursorMsg = new JsonObject();
                cursorMsg.addProperty("type", "CURSOR_UPDATE");
                cursorMsg.addProperty("docId", currentDocId);
                cursorMsg.addProperty("userId", userId);
                cursorMsg.addProperty("position", newPos.intValue());
                //connection.sendOperation("CURSOR_UPDATE", currentDocId, userId, cursorMsg);
            }
        });
    }

    public void updateRemoteCursor(String userId, int position) {
        Platform.runLater(() -> {
            Line cursor = remoteCursors.computeIfAbsent(userId, id -> {
                Color color = getColorForUser(id);
                Line newCursor = new Line(0, 0, 0, 15);
                newCursor.setStroke(color);
                newCursor.setStrokeWidth(2);
                this.getChildren().add(newCursor);
                return newCursor;
            });

            // Simplified position calculation - adjust based on your font metrics
            int row = position / textArea.getPrefColumnCount();
            int col = position % textArea.getPrefColumnCount();
            cursor.setStartX(col * 8 + 5);
            cursor.setStartY(row * 20 + 5);
            cursor.setEndX(col * 8 + 5);
            cursor.setEndY(row * 20 + 20);
        });
    }

    private Color getColorForUser(String userId) {
        return userColors.computeIfAbsent(userId,
                id -> Color.hsb(Math.abs(id.hashCode() % 360), 0.9, 0.9));
    }

    public TextArea getTextArea() {
        return textArea;
    }

    public void clearRemoteCursors() {
        Platform.runLater(() -> {
            this.getChildren().removeAll(remoteCursors.values());
            remoteCursors.clear();
        });
    }
}