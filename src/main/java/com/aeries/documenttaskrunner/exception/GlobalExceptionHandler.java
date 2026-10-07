package com.aeries.documenttaskrunner.exception;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTaskNotFound(TaskNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(CycleDetectedException.class)
    public ResponseEntity<Map<String, Object>> handleCycle(CycleDetectedException ex) {
        return build(HttpStatus.BAD_REQUEST, "CIRCULAR_DEPENDENCY", ex.getMessage());
    }

    @ExceptionHandler(UnknownDependencyException.class)
    public ResponseEntity<Map<String, Object>> handleUnknownDependency(UnknownDependencyException ex) {
        return build(HttpStatus.BAD_REQUEST, "UNKNOWN_DEPENDENCY", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Request validation failed");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, "ILLEGAL_TASK_TRANSITION", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", error,
                "message", message
        ));
    }
}
