package com.documenttaskrunner.controller;

import com.documenttaskrunner.dto.StatsResponse;
import com.documenttaskrunner.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatsController {
    private final TaskService taskService;

    public StatsController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/tasks/stats")
    public ResponseEntity<StatsResponse> stats() {
        TaskService.StatsResponse stats = taskService.getStats();
        return ResponseEntity.ok(new StatsResponse(stats.running(), stats.waiting(), stats.retryWaiting(), stats.succeeded(), stats.failed(), stats.blocked(), stats.cancelled()));
    }
}
