package org.example.client.crdt;

import org.example.client.model.ClientEditorOperation;
import java.util.ArrayList;
import java.util.List;

public class SyncEngine {
    private String lastKnownText;

    public SyncEngine(String initialText) {
        this.lastKnownText = initialText;
    }

    public List<ClientEditorOperation> calculateDiff(String newText) {
        List<ClientEditorOperation> operations = new ArrayList<>();

        // Handle insertions
        if (newText.length() > lastKnownText.length()) {
            System.out.println("this is SyncEngine");
            int pos = 0;
            while (pos < lastKnownText.length() &&
                    lastKnownText.charAt(pos) == newText.charAt(pos)) {
                pos++;
            }
            String inserted = newText.substring(pos, newText.length());
            operations.add(new ClientEditorOperation("INSERT", pos, inserted));
        }
        // Handle deletions
        else if (newText.length() < lastKnownText.length()) {
            int pos = 0;
            while (pos < newText.length() &&
                    lastKnownText.charAt(pos) == newText.charAt(pos)) {
                pos++;
            }
            int length = lastKnownText.length() - newText.length();
            operations.add(new ClientEditorOperation("DELETE", pos, length));
        }

        this.lastKnownText = newText;
        return operations;
    }

    public String applyOperation(String currentText, ClientEditorOperation operation) {
        switch (operation.getType()) {
            case "INSERT":
                return currentText.substring(0, operation.getPosition()) +
                        operation.getText() +
                        currentText.substring(operation.getPosition());
            case "DELETE":
                return currentText.substring(0, operation.getPosition()) +
                        currentText.substring(operation.getPosition() + operation.getLength());
            default:
                return currentText;
        }
    }
}