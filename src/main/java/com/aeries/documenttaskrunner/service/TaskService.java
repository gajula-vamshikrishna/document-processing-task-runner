package com.aeries.documenttaskrunner.service;

import com.aeries.documenttaskrunner.config.TaskRunnerProperties;
import com.aeries.documenttaskrunner.dependency.DependencyService;
import com.aeries.documenttaskrunner.enums.TaskStatus;
import com.aeries.documenttaskrunner.executor.TaskExecutionResult;
import com.aeries.documenttaskrunner.executor.TaskExecutor;
import com.aeries.documenttaskrunner.model.Task;
import com.aeries.documenttaskrunner.model.TaskAttempt;
import com.aeries.documenttaskrunner.repository.TaskAttemptRepository;
import com.aeries.documenttaskrunner.repository.TaskDependencyRepository;
import com.aeries.documenttaskrunner.repository.TaskRepository;
import com.aeries.documenttaskrunner.retry.RetryPolicy;
import com.aeries.documenttaskrunner.scheduler.TaskWorker;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskAttemptRepository taskAttemptRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskExecutor taskExecutor;
    private final RetryPolicy retryPolicy;
    private final DependencyService dependencyService;
    private final TaskRunnerProperties taskRunnerProperties;
    private final ThreadPoolTaskExecutor taskExecutorPool;

    public TaskService(TaskRepository taskRepository,
                      TaskAttemptRepository taskAttemptRepository,
                      TaskDependencyRepository taskDependencyRepository,
                      TaskExecutor taskExecutor,
                      RetryPolicy retryPolicy,
                      DependencyService dependencyService,
                      TaskRunnerProperties taskRunnerProperties,
                      ThreadPoolTaskExecutor taskExecutorPool) {
        this.taskRepository = taskRepository;
        this.taskAttemptRepository = taskAttemptRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.taskExecutor = taskExecutor;
        this.retryPolicy = retryPolicy;
        this.dependencyService = dependencyService;
        this.taskRunnerProperties = taskRunnerProperties;
        this.taskExecutorPool = taskExecutorPool;
    }

    @Transactional(readOnly = true)
    public Task findById(Long taskId) {
        return taskRepository.findById(taskId).orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
    }

    @Transactional(readOnly = true)
    public List<Task> findReadyTasks() {
        return taskRepository.findReadyTasks(List.of(TaskStatus.WAITING, TaskStatus.RETRY_WAITING), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public long countRunning() {
        return taskRepository.countByStatus(TaskStatus.RUNNING);
    }

    @Transactional
    public boolean tryAdmitTask(Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        if (task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.SUCCEEDED || task.getStatus() == TaskStatus.FAILED || task.getStatus() == TaskStatus.BLOCKED) {
            return false;
        }
        if (task.getStatus() == TaskStatus.RETRY_WAITING && task.getNextRunAt() != null && task.getNextRunAt().isAfter(LocalDateTime.now())) {
            return false;
        }
        if (!dependencyService.allDependenciesSucceeded(task)) {
            return false;
        }
        if (countRunning() >= taskRunnerProperties.getMaxConcurrency()) {
            return false;
        }

        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setCompletedAt(null);
        task.setErrorMessage(null);
        task.setNextRunAt(null);
        taskRepository.save(task);

        TaskAttempt attempt = new TaskAttempt();
        attempt.setTaskId(task.getId());
        attempt.setAttemptNumber(task.getAttemptCount());
        attempt.setStartedAt(task.getStartedAt());
        attempt.setStatus(TaskStatus.RUNNING);
        taskAttemptRepository.save(attempt);

        log.info("Task {} ({}) admitted as RUNNING on attempt {}", task.getId(), task.getName(), task.getAttemptCount());
        return true;
    }

    @Transactional
    public void submitForExecution(Long taskId) {
        taskExecutorPool.execute(new TaskWorker(this, taskId));
    }

    @Transactional
    public void executeTask(Long taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        if (task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.BLOCKED || task.getStatus() == TaskStatus.SUCCEEDED || task.getStatus() == TaskStatus.FAILED) {
            return;
        }

        TaskExecutionResult result = taskExecutor.execute(task);
        if (result.success()) {
            completeSuccess(task);
        } else {
            completeFailure(task, result.errorMessage());
        }
    }

    @Transactional
    public void completeSuccess(Task task) {
        task.setStatus(TaskStatus.SUCCEEDED);
        task.setCompletedAt(LocalDateTime.now());
        task.setErrorMessage(null);
        task.setNextRunAt(null);
        taskRepository.save(task);

        TaskAttempt attempt = taskAttemptRepository.findByTaskIdAndAttemptNumber(task.getId(), task.getAttemptCount())
                .orElseGet(TaskAttempt::new);
        attempt.setTaskId(task.getId());
        attempt.setAttemptNumber(task.getAttemptCount());
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setStatus(TaskStatus.SUCCEEDED);
        attempt.setErrorMessage(null);
        taskAttemptRepository.save(attempt);

        dependencyService.reEvaluateDependents(task.getId());
        log.info("Task {} ({}) succeeded.", task.getId(), task.getName());
    }

    @Transactional
    public void completeFailure(Task task, String errorMessage) {
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(LocalDateTime.now());

        if (task.getAttemptCount() >= task.getMaxAttempts()) {
            task.setStatus(TaskStatus.FAILED);
            task.setNextRunAt(null);
            taskRepository.save(task);
            log.info("Task {} ({}) failed permanently.", task.getId(), task.getName());

            TaskAttempt attempt = taskAttemptRepository.findByTaskIdAndAttemptNumber(task.getId(), task.getAttemptCount())
                    .orElseGet(TaskAttempt::new);
            attempt.setTaskId(task.getId());
            attempt.setAttemptNumber(task.getAttemptCount());
            attempt.setCompletedAt(LocalDateTime.now());
            attempt.setStatus(TaskStatus.FAILED);
            attempt.setErrorMessage(errorMessage);
            taskAttemptRepository.save(attempt);

            dependencyService.reEvaluateDependents(task.getId());
            return;
        }

        task.setStatus(TaskStatus.RETRY_WAITING);
        long delayMs = retryPolicy.delayForAttempt(task.getAttemptCount());
        task.setNextRunAt(LocalDateTime.now().plusNanos(delayMs * 1_000_000L));
        taskRepository.save(task);
        log.info("Task {} ({}) scheduled for retry after {} ms.", task.getId(), task.getName(), delayMs);

        TaskAttempt attempt = taskAttemptRepository.findByTaskIdAndAttemptNumber(task.getId(), task.getAttemptCount())
                .orElseGet(TaskAttempt::new);
        attempt.setTaskId(task.getId());
        attempt.setAttemptNumber(task.getAttemptCount());
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setStatus(TaskStatus.FAILED);
        attempt.setErrorMessage(errorMessage);
        taskAttemptRepository.save(attempt);
    }

    @Transactional(readOnly = true)
    public List<Task> getTasksByDocument(Long documentId) {
        return taskRepository.findByDocumentId(documentId);
    }

    @Transactional
    public Task cancelTask(Long taskId) {
        Task task = findById(taskId);
        if (task.getStatus() == TaskStatus.SUCCEEDED || task.getStatus() == TaskStatus.FAILED || task.getStatus() == TaskStatus.BLOCKED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new IllegalStateException("Task cannot be cancelled from status: " + task.getStatus());
        }
        task.setStatus(TaskStatus.CANCELLED);
        task.setNextRunAt(null);
        task.setCompletedAt(LocalDateTime.now());
        task.setErrorMessage("Cancelled by user");
        taskRepository.save(task);
        dependencyService.reEvaluateDependents(taskId);
        return task;
    }

    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        long running = taskRepository.countByStatus(TaskStatus.RUNNING);
        long waiting = taskRepository.countByStatus(TaskStatus.WAITING);
        long retryWaiting = taskRepository.countByStatus(TaskStatus.RETRY_WAITING);
        long succeeded = taskRepository.countByStatus(TaskStatus.SUCCEEDED);
        long failed = taskRepository.countByStatus(TaskStatus.FAILED);
        long blocked = taskRepository.countByStatus(TaskStatus.BLOCKED);
        long cancelled = taskRepository.countByStatus(TaskStatus.CANCELLED);
        return new StatsResponse(running, waiting, retryWaiting, succeeded, failed, blocked, cancelled);
    }

    @Transactional
    public void recalculateDependents(Long taskId) {
        dependencyService.reEvaluateDependents(taskId);
    }

    public record StatsResponse(long running, long waiting, long retryWaiting, long succeeded, long failed, long blocked, long cancelled) {
    }
}
