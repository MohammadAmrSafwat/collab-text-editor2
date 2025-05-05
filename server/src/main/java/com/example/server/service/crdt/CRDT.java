package com.example.server.service.crdt;

import java.time.Instant;
import java.util.*;

public class CRDT {
    private final CRDTNode root;
    private final Map<String, CRDTNode> nodesById; // [userId,clock] -> node
    private final Map<String, List<CRDTNode>> childrenByParent; // parentId -> children

    public CRDT() {
        this.root = new CRDTNode(null, null, null, null, false);
        this.nodesById = new HashMap<>();
        this.childrenByParent = new HashMap<>();
        this.childrenByParent.put("root", new ArrayList<>());
    }
    //first step creation of operation
    public CRDTOperation createInsertOperation(int position, String userId, char value) {
        List<CRDTNode> nodes = getSortedNodes();
        CRDTOperation.NodeId parentId = null;

        // Determine parent based on position
        if (position == 0) {
            parentId = null; // Will use root as parent
        } else if (position > 0 && position <= nodes.size()) {
            CRDTNode parent = nodes.get(position - 1);
            parentId = new CRDTOperation.NodeId(parent.getUserId(), parent.getClock());
        } else {
            throw new IllegalArgumentException("Invalid insert position");
        }

        return new CRDTOperation(
                CRDTOperation.OperationType.INSERT,
                userId,
                Instant.now().toString(),
                value,
                parentId,
                null
        );
    }
    public CRDTOperation createDeleteOperation(int position, String userId) {
        List<CRDTNode> nodes = getSortedNodes();
        if (position < 0 || position >= nodes.size()) {
            throw new IllegalArgumentException("Invalid delete position");
        }

        CRDTNode target = nodes.get(position);
        return new CRDTOperation(
                CRDTOperation.OperationType.DELETE,
                userId,
                Instant.now().toString(),
                null,
                null,
                new CRDTOperation.NodeId(target.getUserId(), target.getClock())
        );
    }
    //apply the operation
    public synchronized void applyOperation(CRDTOperation operation) {
        if (operation.getOp() == CRDTOperation.OperationType.INSERT) {
            handleInsert(operation);
        } else {
            handleDelete(operation);
        }
    }
    //handle the two operations
    private void handleInsert(CRDTOperation operation) {
        String nodeId = operation.getUserId() + "," + operation.getClock();
        if (nodesById.containsKey(nodeId)) return;

        String parentId = operation.getParentId() != null ?
                operation.getParentId().getUserId() + "," + operation.getParentId().getClock() : "root";

        CRDTNode parent = "root".equals(parentId) ? root : nodesById.get(parentId);
        if (parent == null) parent = root; // Fallback to root if parent not found

        CRDTNode newNode = new CRDTNode(operation.getUserId(),
                operation.getClock(), operation.getValue(), parent, false);

        nodesById.put(nodeId, newNode);
        childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(newNode);
    }

    private void handleDelete(CRDTOperation operation) {
        String targetId = operation.getTargetId().getUserId() + "," + operation.getTargetId().getClock();
        CRDTNode node = nodesById.get(targetId);
        if (node != null) node.setDeleted(true);
    }

    public synchronized String getContent() {
        List<CRDTNode> sorted = getSortedNodes();
        StringBuilder content = new StringBuilder();
        for (CRDTNode node : sorted) {
            if (!node.isDeleted()) content.append(node.getValue());
        }
        return content.toString();
    }

    private List<CRDTNode> getSortedNodes() {
        List<CRDTNode> result = new ArrayList<>();
        traverseTree(root, result);
        return result;
    }

    private void traverseTree(CRDTNode node, List<CRDTNode> result) {
        if (node != root) result.add(node);

        String nodeId = node != root ? node.getUserId() + "," + node.getClock() : "root";
        List<CRDTNode> children = childrenByParent.getOrDefault(nodeId, Collections.emptyList());

        // Sort by: 1. Clock descending, 2. UserId ascending
        children.sort((a, b) -> {
            int clockCompare = b.getClock().compareTo(a.getClock());
            return clockCompare != 0 ? clockCompare : a.getUserId().compareTo(b.getUserId());
        });

        for (CRDTNode child : children) traverseTree(child, result);//recursion
    }

    // Helper method to find node at position (1-based)
    public CRDTNode getNodeAtPosition(int position) {
        List<CRDTNode> nodes = getSortedNodes();
        if (position < 1 || position > nodes.size()) return null;
        return nodes.get(position - 1);
    }
}