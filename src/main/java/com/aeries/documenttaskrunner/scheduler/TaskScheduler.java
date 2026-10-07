package com.aeries.documenttaskrunner.scheduler;

import com.aeries.documenttaskrunner.model.Task;
import com.aeries.documenttaskrunner.service.TaskService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("documentTaskScheduler")
public class TaskScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private final TaskService taskService;

    public TaskScheduler(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelayString = "${task-runner.scheduler.polling-interval-ms}")
    public void scheduleReadyTasks() {
        List<Task> readyTasks = taskService.findReadyTasks();
        for (Task task : readyTasks) {
            boolean admitted = taskService.tryAdmitTask(task.getId());
            if (admitted) {
                log.info("Admitted task {} ({}) for execution.", task.getId(), task.getName());
                taskService.submitForExecution(task.getId());
            }
        }
    }
}
