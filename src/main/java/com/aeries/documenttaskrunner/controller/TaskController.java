package com.aeries.documenttaskrunner.controller;

import com.aeries.documenttaskrunner.dto.TaskResponse;
import com.aeries.documenttaskrunner.model.Task;
import com.aeries.documenttaskrunner.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable Long taskId) {
        Task task = taskService.findById(taskId);
        return ResponseEntity.ok(new TaskResponse(
                task.getId(),
                task.getName(),
                task.getType(),
                task.getStatus(),
                task.getAttemptCount(),
                task.getMaxAttempts(),
                task.getFailureProbability(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getCompletedAt()));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ResponseEntity<TaskResponse> cancelTask(@PathVariable Long taskId) {
        Task task = taskService.cancelTask(taskId);
        return ResponseEntity.ok(new TaskResponse(
                task.getId(),
                task.getName(),
                task.getType(),
                task.getStatus(),
                task.getAttemptCount(),
                task.getMaxAttempts(),
                task.getFailureProbability(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getCompletedAt()));
    }
}
