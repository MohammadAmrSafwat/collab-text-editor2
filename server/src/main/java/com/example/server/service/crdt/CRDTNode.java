package com.example.server.service.crdt;
import java.util.ArrayList;
import java.util.List;

public class CRDTNode {
    private String userId;
    private String clock;
    private Character value;
    private CRDTNode parent;
    private boolean deleted;
    private List<CRDTNode> children;

    public CRDTNode(String userId, String clock, Character value, CRDTNode parent, boolean deleted) {
        this.userId = userId;
        this.clock = clock;
        this.value = value;
        this.parent = parent;
        this.deleted = deleted;
        this.children = new ArrayList<>();
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

    public CRDTNode getParent() {
        return parent;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public List<CRDTNode> getChildren() {
        return new ArrayList<>(children);
    }

    public void addChild(CRDTNode child) {
        children.add(child);
    }

    public String getId() {
        return userId + "," + clock;
    }
}