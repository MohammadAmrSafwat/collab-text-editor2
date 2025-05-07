package com.example.server.service.crdt;

import java.time.Instant;
import java.util.*;

public class CRDT {
    // The root node of the CRDT tree structure
    private final CRDTNode root;
    // Map to store nodes by their unique identifier (userId + clock)
    private final Map<String, CRDTNode> nodesById; // [userId,clock] -> node
    // Map to store children nodes by their parent's identifier
    private final Map<String, List<CRDTNode>> childrenByParent; // parentId -> children

    public CRDT() {
        // Initialize root node with null values (special node)
        this.root = new CRDTNode(null, null, null, null, false);
        // Initialize empty maps for node storage
        this.nodesById = new HashMap<>();
        this.childrenByParent = new HashMap<>();
        // Initialize root's children list
        this.childrenByParent.put("root", new ArrayList<>());
    }

    // Creates an INSERT operation for adding a new character at specified position
    public CRDTOperation createInsertOperation(int position, String userId, char value) {
        // Get all nodes in sorted order (document order)
        List<CRDTNode> nodes = getSortedNodes();
        CRDTOperation.NodeId parentId = null;

        // Determine parent based on position:
        if (position == 0) {
            // If position is 0, parent is root (represented as null)
            parentId = null; // Will use root as parent
        } else if (position > 0 && position <= nodes.size()) {
            // For other positions, parent is the node before the insertion point
            CRDTNode parent = nodes.get(position - 1);
            parentId = new CRDTOperation.NodeId(parent.getUserId(), parent.getClock());
        } else {
            throw new IllegalArgumentException("Invalid insert position");
        }

        // Create and return new INSERT operation
        return new CRDTOperation(
                CRDTOperation.OperationType.INSERT,
                userId,
                Instant.now().toString(),
                value,
                parentId,
                null
        );
    }

    // Creates a DELETE operation for removing a character at specified position
    public CRDTOperation createDeleteOperation(int position, String userId) {
        // Get all nodes in sorted order
        List<CRDTNode> nodes = getSortedNodes();
        // Validate position
        if (position < 0 || position >= nodes.size()) {
            throw new IllegalArgumentException("Invalid delete position");
        }

        // Get the node to be deleted
        CRDTNode target = nodes.get(position);
        // Create and return new DELETE operation
        return new CRDTOperation(
                CRDTOperation.OperationType.DELETE,
                userId,
                Instant.now().toString(),
                null,
                null,
                new CRDTOperation.NodeId(target.getUserId(), target.getClock())
        );
    }

    // Applies an operation to the CRDT (thread-safe)
    public synchronized void applyOperation(CRDTOperation operation) {
        // Route to appropriate handler based on operation type
        if (operation.getOp() == CRDTOperation.OperationType.INSERT) {
            handleInsert(operation);
        } else {
            handleDelete(operation);
        }
    }

    // Handles INSERT operations
    private void handleInsert(CRDTOperation operation) {
        // Create unique node identifier
        String nodeId = operation.getUserId() + "," + operation.getClock();
        // Skip if node already exists (idempotency)
        if (nodesById.containsKey(nodeId)) return;

        // Determine parent identifier (root if parent is null)
        String parentId = operation.getParentId() != null ?
                operation.getParentId().getUserId() + "," + operation.getParentId().getClock() : "root";

        // Get parent node (fallback to root if not found)
        CRDTNode parent = "root".equals(parentId) ? root : nodesById.get(parentId);
        if (parent == null) parent = root; // Fallback to root if parent not found

        // Create new node with operation data
        CRDTNode newNode = new CRDTNode(operation.getUserId(),
                operation.getClock(), operation.getValue(), parent, false);

        // Store the new node
        nodesById.put(nodeId, newNode);
        // Add to parent's children list (creating list if needed)
        childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(newNode);
    }

    // Handles DELETE operations
    private void handleDelete(CRDTOperation operation) {
        // Create target node identifier
        String targetId = operation.getTargetId().getUserId() + "," + operation.getTargetId().getClock();
        // Get the node to be deleted
        CRDTNode node = nodesById.get(targetId);
        // Mark node as deleted (tombstone) if found
        if (node != null) node.setDeleted(true);
    }

    // Returns the current document content as a string
    public synchronized String getContent() {
        // Get all nodes in document order
        List<CRDTNode> sorted = getSortedNodes();
        StringBuilder content = new StringBuilder();
        // Append all non-deleted nodes' values
        for (CRDTNode node : sorted) {
            if (!node.isDeleted()) content.append(node.getValue());
        }
        return content.toString();
    }

    // Returns the length of the document (number of non-deleted characters)
    public synchronized int getLength() {
        int length = 0;
        List<CRDTNode> nodes = getSortedNodes();
        // Count non-deleted nodes
        for (CRDTNode node : nodes) {
            if (!node.isDeleted()) {
                length++;
            }
        }
        return length;
    }

    // Returns all nodes in document order
    private List<CRDTNode> getSortedNodes() {
        List<CRDTNode> result = new ArrayList<>();
        // Start traversal from root
        traverseTree(root, result);
        return result;
    }

    // Recursive helper method for traversing the tree in document order
    private void traverseTree(CRDTNode node, List<CRDTNode> result) {
        // Add current node to result (except root)
        if (node != root) result.add(node);

        // Get node identifier (special case for root)
        String nodeId = node != root ? node.getUserId() + "," + node.getClock() : "root";
        // Get children (empty list if none)
        List<CRDTNode> children = childrenByParent.getOrDefault(nodeId, Collections.emptyList());

        // Sort children by:
        // 1. Clock descending (newer operations first)
        // 2. UserId ascending (for tie-breaking)
        children.sort((a, b) -> {
            int clockCompare = b.getClock().compareTo(a.getClock());
            return clockCompare != 0 ? clockCompare : a.getUserId().compareTo(b.getUserId());
        });

        // Recursively traverse each child
        for (CRDTNode child : children) traverseTree(child, result);
    }
}