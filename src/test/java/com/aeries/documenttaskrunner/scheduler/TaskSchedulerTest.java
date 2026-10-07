package com.aeries.documenttaskrunner.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aeries.documenttaskrunner.config.TaskRunnerProperties;
import com.aeries.documenttaskrunner.dependency.DependencyService;
import com.aeries.documenttaskrunner.enums.TaskStatus;
import com.aeries.documenttaskrunner.executor.TaskExecutionResult;
import com.aeries.documenttaskrunner.executor.TaskExecutor;
import com.aeries.documenttaskrunner.model.Task;
import com.aeries.documenttaskrunner.repository.TaskAttemptRepository;
import com.aeries.documenttaskrunner.repository.TaskDependencyRepository;
import com.aeries.documenttaskrunner.repository.TaskRepository;
import com.aeries.documenttaskrunner.retry.RetryPolicy;
import com.aeries.documenttaskrunner.service.TaskService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class TaskSchedulerTest {
    @Test
    void schedulerOnlyRunsEligibleTasks() {
        TaskService taskService = mock(TaskService.class);
        TaskScheduler scheduler = new TaskScheduler(taskService);
        Task ready = new Task();
        ready.setId(1L);
        ready.setName("validate-document");
        Task blocked = new Task();
        blocked.setId(2L);
        blocked.setName("blocked-task");

        when(taskService.findReadyTasks()).thenReturn(List.of(ready, blocked));
        when(taskService.tryAdmitTask(1L)).thenReturn(true);
        when(taskService.tryAdmitTask(2L)).thenReturn(false);

        scheduler.scheduleReadyTasks();

        org.mockito.Mockito.verify(taskService).submitForExecution(1L);
        org.mockito.Mockito.verify(taskService, org.mockito.Mockito.never()).submitForExecution(2L);
    }

    @Test
    void concurrencyNeverExceedsConfiguredN() throws InterruptedException {
        TaskRunnerProperties properties = new TaskRunnerProperties();
        properties.setMaxConcurrency(2);
        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskAttemptRepository attemptRepository = mock(TaskAttemptRepository.class);
        TaskDependencyRepository dependencyRepository = mock(TaskDependencyRepository.class);
        DependencyService dependencyService = new DependencyService(taskRepository, dependencyRepository);
        RetryPolicy retryPolicy = new RetryPolicy(properties);

        Map<Long, Task> tasks = new HashMap<>();
        for (long i = 1L; i <= 5L; i++) {
            Task task = new Task();
            task.setId(i);
            task.setName("task-" + i);
            task.setStatus(TaskStatus.WAITING);
            tasks.put(i, task);
        }

        when(taskRepository.findById(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(inv -> Optional.ofNullable(tasks.get(inv.getArgument(0))));
        when(taskRepository.save(org.mockito.ArgumentMatchers.any(Task.class))).thenAnswer(inv -> {
            Task task = inv.getArgument(0);
            tasks.put(task.getId(), task);
            return task;
        });
        when(taskRepository.countByStatus(TaskStatus.RUNNING)).thenAnswer(inv -> (long) tasks.values().stream().filter(task -> task.getStatus() == TaskStatus.RUNNING).count());
        when(taskRepository.findReadyTasks(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(dependencyRepository.findByTaskId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());
        when(dependencyRepository.findByDependsOnTaskId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());

        AtomicInteger currentRunning = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(2);
        TaskExecutor fakeExecutor = task -> {
            int current = currentRunning.incrementAndGet();
            maxObserved.accumulateAndGet(current, Math::max);
            latch.countDown();
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                currentRunning.decrementAndGet();
            }
            return new TaskExecutionResult(true, null);
        };

        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(20);
        pool.initialize();

        TaskService service = new TaskService(taskRepository, attemptRepository, dependencyRepository, fakeExecutor, retryPolicy, dependencyService, properties, pool);

        for (Task task : tasks.values()) {
            boolean admitted = service.tryAdmitTask(task.getId());
            if (admitted) {
                service.submitForExecution(task.getId());
            }
        }

        latch.await();
        pool.shutdown();
        pool.getThreadPoolExecutor().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(maxObserved.get() <= 2, "Observed max running tasks: " + maxObserved.get());
        assertEquals(2, maxObserved.get());
    }
}
