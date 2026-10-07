package com.documenttaskrunner.exception;

public class UnknownDependencyException extends RuntimeException {
    public UnknownDependencyException(String message) {
        super(message);
    }
}
