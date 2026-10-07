package com.documenttaskrunner.service;

import com.documenttaskrunner.enums.TaskStatus;
import com.documenttaskrunner.model.Task;
import com.documenttaskrunner.repository.TaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryService {
    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private final TaskRepository taskRepository;

    public RecoveryService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverRunningTasks() {
        List<Task> runningTasks = taskRepository.findByStatus(TaskStatus.RUNNING);
        for (Task task : runningTasks) {
            task.setStatus(TaskStatus.WAITING);
            task.setStartedAt(null);
            task.setNextRunAt(LocalDateTime.now());
            task.setErrorMessage("Recovered after restart; task was RUNNING and requeued as WAITING.");
            taskRepository.save(task);
            log.info("Recovered task {} {} after restart", task.getId(), task.getName());
        }
    }
}
