package com.aeries.documenttaskrunner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class SubmitWorkflowRequest {
    @Valid
    @NotNull
    private DocumentRequest document;

    @Valid
    @NotNull
    private List<TaskRequest> tasks = new ArrayList<>();

    public DocumentRequest getDocument() {
        return document;
    }

    public void setDocument(DocumentRequest document) {
        this.document = document;
    }

    public List<TaskRequest> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskRequest> tasks) {
        this.tasks = tasks == null ? new ArrayList<>() : tasks;
    }
}
