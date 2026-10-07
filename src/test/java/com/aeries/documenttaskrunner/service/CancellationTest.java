package com.aeries.documenttaskrunner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aeries.documenttaskrunner.config.TaskRunnerProperties;
import com.aeries.documenttaskrunner.dependency.DependencyService;
import com.aeries.documenttaskrunner.enums.TaskStatus;
import com.aeries.documenttaskrunner.executor.TaskExecutionResult;
import com.aeries.documenttaskrunner.executor.TaskExecutor;
import com.aeries.documenttaskrunner.model.Task;
import com.aeries.documenttaskrunner.model.TaskDependency;
import com.aeries.documenttaskrunner.repository.TaskAttemptRepository;
import com.aeries.documenttaskrunner.repository.TaskDependencyRepository;
import com.aeries.documenttaskrunner.repository.TaskRepository;
import com.aeries.documenttaskrunner.retry.RetryPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class CancellationTest {
    @Test
    void waitingTaskCanBeCancelledAndDependentsBlocked() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskAttemptRepository attemptRepository = mock(TaskAttemptRepository.class);
        TaskDependencyRepository dependencyRepository = mock(TaskDependencyRepository.class);
        DependencyService dependencyService = new DependencyService(taskRepository, dependencyRepository);
        TaskRunnerProperties properties = new TaskRunnerProperties();
        properties.setMaxConcurrency(2);
        TaskExecutor executor = task -> new TaskExecutionResult(true, null);
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.initialize();

        Map<Long, Task> tasks = new HashMap<>();
        Task parent = new Task(); parent.setId(1L); parent.setName("A"); parent.setStatus(TaskStatus.SUCCEEDED);
        Task cancelled = new Task(); cancelled.setId(2L); cancelled.setName("B"); cancelled.setStatus(TaskStatus.WAITING);
        Task unrelated = new Task(); unrelated.setId(3L); unrelated.setName("C"); unrelated.setStatus(TaskStatus.WAITING);
        Task dependent = new Task(); dependent.setId(4L); dependent.setName("D"); dependent.setStatus(TaskStatus.WAITING);
        tasks.put(1L, parent); tasks.put(2L, cancelled); tasks.put(3L, unrelated); tasks.put(4L, dependent);

        when(taskRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(tasks.get(inv.getArgument(0))));
        when(taskRepository.save(org.mockito.ArgumentMatchers.any(Task.class))).thenAnswer(inv -> {
            Task task = inv.getArgument(0);
            tasks.put(task.getId(), task);
            return task;
        });
        when(taskRepository.countByStatus(TaskStatus.RUNNING)).thenReturn(0L);
        when(dependencyRepository.findByTaskId(2L)).thenReturn(List.of(new TaskDependency(2L, 1L)));
        when(dependencyRepository.findByTaskId(4L)).thenReturn(List.of(new TaskDependency(4L, 2L)));
        when(dependencyRepository.findByDependsOnTaskId(2L)).thenReturn(List.of(new TaskDependency(4L, 2L)));
        when(dependencyRepository.findByDependsOnTaskId(1L)).thenReturn(List.of(new TaskDependency(2L, 1L)));

        TaskService taskService = new TaskService(taskRepository, attemptRepository, dependencyRepository, executor, new RetryPolicy(properties), dependencyService, properties, pool);
        taskService.cancelTask(2L);

        assertEquals(TaskStatus.CANCELLED, cancelled.getStatus());
        assertEquals(TaskStatus.BLOCKED, dependent.getStatus());
        assertEquals(TaskStatus.WAITING, unrelated.getStatus());
    }
}
