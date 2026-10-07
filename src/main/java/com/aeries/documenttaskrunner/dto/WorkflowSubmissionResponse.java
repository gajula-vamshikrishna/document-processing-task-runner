package com.aeries.documenttaskrunner.dto;

import java.util.List;

public record WorkflowSubmissionResponse(Long documentId, List<Long> taskIds) {
}
