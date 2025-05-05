package com.example.server.service.crdt;
public class CRDTOperation {
    public enum OperationType {
        INSERT,
        DELETE
    }

    private OperationType op;
    private String userId;
    private String clock;
    private Character value;
    private NodeId parentId; // For insert operations
    private NodeId targetId; // For delete operations

    public CRDTOperation(OperationType op, String userId, String clock,
                         Character value, NodeId parentId, NodeId targetId) {
        this.op = op;
        this.userId = userId;
        this.clock = clock;
        this.value = value;
        this.parentId = parentId;
        this.targetId = targetId;
    }

    public OperationType getOp() {
        return op;
    }

    public String getUserId() {
        return userId;
    }

    public String getClock() {
        return clock;
    }

    public Character getValue() {
        return value;
    }

    public NodeId getParentId() {
        return parentId;
    }

    public NodeId getTargetId() {
        return targetId;
    }

    public static class NodeId {
        private String userId;
        private String clock;

        public NodeId(String userId, String clock) {
            this.userId = userId;
            this.clock = clock;
        }

        public String getUserId() {
            return userId;
        }

        public String getClock() {
            return clock;
        }
    }
}
