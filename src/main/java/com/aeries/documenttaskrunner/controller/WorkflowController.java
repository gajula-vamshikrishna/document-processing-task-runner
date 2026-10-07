package com.aeries.documenttaskrunner.controller;

import com.aeries.documenttaskrunner.dto.SubmitWorkflowRequest;
import com.aeries.documenttaskrunner.dto.WorkflowSubmissionResponse;
import com.aeries.documenttaskrunner.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorkflowController {
    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/workflows")
    public ResponseEntity<WorkflowSubmissionResponse> submitWorkflow(@Valid @RequestBody SubmitWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.submitWorkflow(request));
    }
}
