package com.documenttaskrunner.enums;

public enum TaskStatus {
    WAITING,
    RETRY_WAITING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    BLOCKED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == BLOCKED || this == CANCELLED;
    }
}
