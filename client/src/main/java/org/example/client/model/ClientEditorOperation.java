package org.example.client.model;

public class ClientEditorOperation {
    private String type; // "INSERT" or "DELETE"
    private int position;
    private String text; // For INSERT
    private int length;  // For DELETE

    public ClientEditorOperation(String type, int position, String text) {
        this.type = type;
        this.position = position;
        this.text = text;
    }

    public ClientEditorOperation(String type, int position, int length) {
        this.type = type;
        this.position = position;
        this.length = length;
    }

    // Getters and setters
    public String getType() { return type; }
    public int getPosition() { return position; }
    public String getText() { return text; }
    public int getLength() { return length; }
}