package com.example.server.model;


import com.example.server.service.crdt.CRDTOperation;

public class OperationResponse {
    private CRDTOperation operation;
    private String documentState;

    // Constructor
    public OperationResponse(CRDTOperation operation, String documentState) {
        this.operation = operation;
        this.documentState = documentState;
    }

    // Getters
    public CRDTOperation getOperation() {
        return operation;
    }

    public String getDocumentState() {
        return documentState;
    }

    // Setters (if needed)
    public void setOperation(CRDTOperation operation) {
        this.operation = operation;
    }

    public void setDocumentState(String documentState) {
        this.documentState = documentState;
    }
}