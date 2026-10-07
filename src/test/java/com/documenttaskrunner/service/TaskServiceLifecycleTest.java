package com.documenttaskrunner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.documenttaskrunner.config.TaskRunnerProperties;
import com.documenttaskrunner.dependency.DependencyService;
import com.documenttaskrunner.enums.TaskStatus;
import com.documenttaskrunner.exception.TaskNotFoundException;
import com.documenttaskrunner.executor.TaskExecutionResult;
import com.documenttaskrunner.executor.TaskExecutor;
import com.documenttaskrunner.model.Task;
import com.documenttaskrunner.repository.TaskAttemptRepository;
import com.documenttaskrunner.repository.TaskDependencyRepository;
import com.documenttaskrunner.repository.TaskRepository;
import com.documenttaskrunner.retry.RetryPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class TaskServiceLifecycleTest {
    @Test
    void runningTaskCancellationIsRejected() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskAttemptRepository attemptRepository = mock(TaskAttemptRepository.class);
        TaskDependencyRepository dependencyRepository = mock(TaskDependencyRepository.class);
        TaskRunnerProperties properties = new TaskRunnerProperties();
        properties.setMaxConcurrency(2);
        TaskExecutor executor = task -> new TaskExecutionResult(true, null);
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.initialize();

        Task task = new Task();
        task.setId(10L);
        task.setName("running-task");
        task.setStatus(TaskStatus.RUNNING);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        TaskService service = new TaskService(taskRepository, attemptRepository, dependencyRepository, executor,
                new RetryPolicy(properties), new DependencyService(taskRepository, dependencyRepository), properties, pool);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.cancelTask(10L));
        assertEquals("Task cannot be cancelled while RUNNING; it must finish first.", ex.getMessage());
    }

    @Test
    void timedOutExecutionRetries() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskAttemptRepository attemptRepository = mock(TaskAttemptRepository.class);
        TaskDependencyRepository dependencyRepository = mock(TaskDependencyRepository.class);
        TaskRunnerProperties properties = new TaskRunnerProperties();
        properties.setMaxConcurrency(2);
        properties.getExecution().setTimeoutSeconds(1);
        TaskExecutor executor = task -> {
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new TaskExecutionResult(true, null);
        };
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.initialize();

        Map<Long, Task> tasks = new HashMap<>();
        Task task = new Task();
        task.setId(22L);
        task.setName("slow-task");
        task.setStatus(TaskStatus.RUNNING);
        task.setAttemptCount(1);
        task.setMaxAttempts(3);
        tasks.put(22L, task);

        when(taskRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(tasks.get(inv.getArgument(0))));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task saved = inv.getArgument(0);
            tasks.put(saved.getId(), saved);
            return saved;
        });
        when(attemptRepository.findByTaskIdAndAttemptNumber(22L, 1)).thenReturn(Optional.empty());

        TaskService service = new TaskService(taskRepository, attemptRepository, dependencyRepository, executor,
                new RetryPolicy(properties), new DependencyService(taskRepository, dependencyRepository), properties, pool);

        service.executeTask(22L);

        assertEquals(TaskStatus.RETRY_WAITING, task.getStatus());
        assertEquals("Task execution timed out after 1 seconds", task.getErrorMessage());
        assertNotNull(task.getNextRunAt());
    }

    @Test
    void missingTaskThrowsNotFound() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskAttemptRepository attemptRepository = mock(TaskAttemptRepository.class);
        TaskDependencyRepository dependencyRepository = mock(TaskDependencyRepository.class);
        TaskRunnerProperties properties = new TaskRunnerProperties();
        TaskExecutor executor = task -> new TaskExecutionResult(true, null);
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.initialize();

        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        TaskService service = new TaskService(taskRepository, attemptRepository, dependencyRepository, executor,
                new RetryPolicy(properties), new DependencyService(taskRepository, dependencyRepository), properties, pool);

        assertThrows(TaskNotFoundException.class, () -> service.findById(99L));
    }
}
