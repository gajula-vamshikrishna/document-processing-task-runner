package com.aeries.documenttaskrunner.dto;

import com.aeries.documenttaskrunner.enums.TaskStatus;
import com.aeries.documenttaskrunner.enums.TaskType;
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
