package com.documenttaskrunner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.documenttaskrunner.enums.TaskStatus;
import com.documenttaskrunner.model.Task;
import com.documenttaskrunner.repository.TaskRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecoveryTest {
    @Test
    void runningTasksAreResetToWaitingOnStartup() {
        TaskRepository repository = mock(TaskRepository.class);
        RecoveryService recoveryService = new RecoveryService(repository);

        Task running = new Task();
        running.setId(1L);
        running.setName("validate-document");
        running.setStatus(TaskStatus.RUNNING);

        Task terminal = new Task();
        terminal.setId(2L);
        terminal.setName("completed-task");
        terminal.setStatus(TaskStatus.SUCCEEDED);

        when(repository.findByStatus(TaskStatus.RUNNING)).thenReturn(List.of(running));
        when(repository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        recoveryService.recoverRunningTasks();

        assertEquals(TaskStatus.WAITING, running.getStatus());
        assertNull(running.getStartedAt());
        assertEquals(TaskStatus.SUCCEEDED, terminal.getStatus());
    }
}
