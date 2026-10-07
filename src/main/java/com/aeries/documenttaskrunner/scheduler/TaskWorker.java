package com.aeries.documenttaskrunner.scheduler;

import com.aeries.documenttaskrunner.service.TaskService;

public class TaskWorker implements Runnable {
    private final TaskService taskService;
    private final Long taskId;

    public TaskWorker(TaskService taskService, Long taskId) {
        this.taskService = taskService;
        this.taskId = taskId;
    }

    @Override
    public void run() {
        taskService.executeTask(taskId);
    }
}
