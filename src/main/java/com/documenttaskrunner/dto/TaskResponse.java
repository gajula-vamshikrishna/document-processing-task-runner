package com.documenttaskrunner.dto;

import com.documenttaskrunner.enums.TaskStatus;
import com.documenttaskrunner.enums.TaskType;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String name,
        TaskType type,
        TaskStatus status,
        int attempts,
        int maxAttempts,
        double failureProbability,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
