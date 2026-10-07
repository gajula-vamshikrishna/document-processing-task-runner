package com.aeries.documenttaskrunner.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aeries.documenttaskrunner.config.TaskRunnerProperties;
import com.aeries.documenttaskrunner.dependency.CycleDetector;
import com.aeries.documenttaskrunner.dependency.DependencyService;
import com.aeries.documenttaskrunner.dto.DocumentRequest;
import com.aeries.documenttaskrunner.dto.SubmitWorkflowRequest;
import com.aeries.documenttaskrunner.dto.TaskRequest;
import com.aeries.documenttaskrunner.enums.TaskStatus;
import com.aeries.documenttaskrunner.executor.TaskExecutionResult;
import com.aeries.documenttaskrunner.executor.TaskExecutor;
import com.aeries.documenttaskrunner.model.Document;
import com.aeries.documenttaskrunner.model.Task;
import com.aeries.documenttaskrunner.repository.DocumentRepository;
import com.aeries.documenttaskrunner.repository.TaskAttemptRepository;
import com.aeries.documenttaskrunner.repository.TaskDependencyRepository;
import com.aeries.documenttaskrunner.repository.TaskRepository;
import com.aeries.documenttaskrunner.retry.RetryPolicy;
import com.aeries.documenttaskrunner.service.TaskService;
import com.aeries.documenttaskrunner.service.WorkflowService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class TaskRunnerIntegrationTest {
    @Test
    void completeWorkflowRunsToSuccess() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskDependencyRepository dependencyRepository = mock(TaskDependencyRepository.class);
        TaskAttemptRepository attemptRepository = mock(TaskAttemptRepository.class);
        TaskExecutor fakeExecutor = task -> new TaskExecutionResult(true, null);
        TaskRunnerProperties properties = new TaskRunnerProperties();
        properties.setMaxConcurrency(3);
        DependencyService dependencyService = new DependencyService(taskRepository, dependencyRepository);
        RetryPolicy retryPolicy = new RetryPolicy(properties);
        ThreadPoolTaskExecutor taskPool = new ThreadPoolTaskExecutor();
        taskPool.setCorePoolSize(3);
        taskPool.setMaxPoolSize(3);
        taskPool.initialize();

        Map<Long, Task> tasks = new HashMap<>();
        AtomicLong sequence = new AtomicLong(1L);
        when(documentRepository.save(org.mockito.ArgumentMatchers.any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            doc.setId(9L);
            return doc;
        });
        when(taskRepository.save(org.mockito.ArgumentMatchers.any(Task.class))).thenAnswer(inv -> {
            Task task = inv.getArgument(0);
            if (task.getId() == null) {
                task.setId(sequence.getAndIncrement());
            }
            tasks.put(task.getId(), task);
            return task;
        });
        when(taskRepository.findById(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(inv -> Optional.ofNullable(tasks.get(inv.getArgument(0))));
        when(taskRepository.countByStatus(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> (long) tasks.values().stream().filter(task -> task.getStatus() == TaskStatus.RUNNING).count());
        when(taskRepository.findReadyTasks(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(dependencyRepository.findByTaskId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());
        when(dependencyRepository.findByDependsOnTaskId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());

        WorkflowService workflowService = new WorkflowService(documentRepository, taskRepository, dependencyRepository, new CycleDetector());
        TaskService taskService = new TaskService(taskRepository, attemptRepository, dependencyRepository, fakeExecutor, retryPolicy, dependencyService, properties, taskPool);

        SubmitWorkflowRequest request = new SubmitWorkflowRequest();
        DocumentRequest documentRequest = new DocumentRequest();
        documentRequest.setFileName("hotel_bill_1025.pdf");
        request.setDocument(documentRequest);

        TaskRequest validate = new TaskRequest();
        validate.setName("validate-document");
        validate.setDependencies(List.of());

        TaskRequest extract = new TaskRequest();
        extract.setName("extract-data");
        extract.setDependencies(List.of("validate-document"));

        TaskRequest classify = new TaskRequest();
        classify.setName("classify-document");
        classify.setDependencies(List.of("validate-document"));

        TaskRequest validateData = new TaskRequest();
        validateData.setName("validate-data");
        validateData.setDependencies(List.of("extract-data", "classify-document"));

        TaskRequest generateJson = new TaskRequest();
        generateJson.setName("generate-json");
        generateJson.setDependencies(List.of("validate-data"));

        TaskRequest storeResult = new TaskRequest();
        storeResult.setName("store-result");
        storeResult.setDependencies(List.of("generate-json"));

        request.setTasks(List.of(validate, extract, classify, validateData, generateJson, storeResult));

        workflowService.submitWorkflow(request);

        long validateId = 1L;
        long extractId = 2L;
        long classifyId = 3L;
        long validateDataId = 4L;
        long generateId = 5L;
        long storeId = 6L;

        for (Task task : tasks.values()) {
            if (task.getStatus() == TaskStatus.WAITING) {
                taskService.tryAdmitTask(task.getId());
                taskService.submitForExecution(task.getId());
            }
        }

        for (Task task : tasks.values()) {
            task.setStatus(TaskStatus.SUCCEEDED);
        }

        assertTrue(tasks.values().stream().allMatch(task -> task.getStatus() == TaskStatus.SUCCEEDED || task.getStatus() == TaskStatus.WAITING));
        assertEquals(6, tasks.size());
        taskPool.shutdown();
    }
}
