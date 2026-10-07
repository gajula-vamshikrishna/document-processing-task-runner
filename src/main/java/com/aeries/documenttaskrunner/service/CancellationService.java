package com.aeries.documenttaskrunner.service;

import com.aeries.documenttaskrunner.enums.TaskStatus;
import com.aeries.documenttaskrunner.model.Task;
import com.aeries.documenttaskrunner.repository.TaskRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancellationService {
    private final TaskRepository taskRepository;
    private final TaskService taskService;

    public CancellationService(TaskRepository taskRepository, TaskService taskService) {
        this.taskRepository = taskRepository;
        this.taskService = taskService;
    }

    @Transactional
    public Task cancel(Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();

        if (task.getStatus() == TaskStatus.SUCCEEDED || task.getStatus() == TaskStatus.FAILED
                || task.getStatus() == TaskStatus.BLOCKED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new IllegalStateException("Task is already in terminal state: " + task.getStatus());
        }

        task.setStatus(TaskStatus.CANCELLED);
        task.setErrorMessage("Cancelled by user");
        task.setNextRunAt(null);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);

        taskService.recalculateDependents(taskId);
        return task;
    }
}
