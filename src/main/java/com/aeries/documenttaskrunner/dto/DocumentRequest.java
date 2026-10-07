package com.aeries.documenttaskrunner.dto;

import jakarta.validation.constraints.NotBlank;

public class DocumentRequest {
    @NotBlank
    private String fileName;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
