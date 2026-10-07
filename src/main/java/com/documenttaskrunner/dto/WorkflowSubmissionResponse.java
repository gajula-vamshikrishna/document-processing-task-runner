package com.documenttaskrunner.dto;

import java.util.List;

public record WorkflowSubmissionResponse(Long documentId, List<Long> taskIds) {
}
