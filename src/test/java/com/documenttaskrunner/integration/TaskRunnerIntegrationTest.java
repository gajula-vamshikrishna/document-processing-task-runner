package com.documenttaskrunner.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.documenttaskrunner.dto.DocumentRequest;
import com.documenttaskrunner.dto.SubmitWorkflowRequest;
import com.documenttaskrunner.dto.TaskRequest;
import com.documenttaskrunner.enums.TaskStatus;
import com.documenttaskrunner.executor.TaskExecutionResult;
import com.documenttaskrunner.executor.TaskExecutor;
import com.documenttaskrunner.model.Task;
import com.documenttaskrunner.repository.TaskRepository;
import com.documenttaskrunner.scheduler.TaskScheduler;
import com.documenttaskrunner.service.WorkflowService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringBootTest(properties = "task-runner.scheduler.enabled=false")
@ActiveProfiles("test")
class TaskRunnerIntegrationTest {
    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private TaskScheduler taskScheduler;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void workflowExecutesInDependencyOrderAndCompletes() throws InterruptedException {
        taskRepository.deleteAll();

        SubmitWorkflowRequest request = new SubmitWorkflowRequest();
        DocumentRequest document = new DocumentRequest();
        document.setFileName("hotel_bill_1025.pdf");
        request.setDocument(document);

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

        long deadline = System.currentTimeMillis() + 20_000L;
        while (System.currentTimeMillis() < deadline) {
            taskScheduler.scheduleReadyTasks();
            if (taskRepository.findAll().stream().allMatch(task -> task.getStatus().isTerminal())) {
                break;
            }
            Thread.sleep(100L);
        }

        List<Task> tasks = taskRepository.findAll();
        assertEquals(6, tasks.size());
        assertTrue(tasks.stream().allMatch(task -> task.getStatus() == TaskStatus.SUCCEEDED),
                "All tasks should finish successfully: " + tasks);

        Task validateTask = tasks.stream().filter(t -> t.getName().equals("validate-document")).findFirst().orElseThrow();
        Task extractTask = tasks.stream().filter(t -> t.getName().equals("extract-data")).findFirst().orElseThrow();
        Task classifyTask = tasks.stream().filter(t -> t.getName().equals("classify-document")).findFirst().orElseThrow();
        Task validateDataTask = tasks.stream().filter(t -> t.getName().equals("validate-data")).findFirst().orElseThrow();
        Task generateJsonTask = tasks.stream().filter(t -> t.getName().equals("generate-json")).findFirst().orElseThrow();
        Task storeResultTask = tasks.stream().filter(t -> t.getName().equals("store-result")).findFirst().orElseThrow();

        assertNotNull(validateTask.getCompletedAt());
        assertTrue(extractTask.getCompletedAt().isAfter(validateTask.getCompletedAt()));
        assertTrue(classifyTask.getCompletedAt().isAfter(validateTask.getCompletedAt()));
        assertTrue(validateDataTask.getCompletedAt().isAfter(extractTask.getCompletedAt()));
        assertTrue(validateDataTask.getCompletedAt().isAfter(classifyTask.getCompletedAt()));
        assertTrue(generateJsonTask.getCompletedAt().isAfter(validateDataTask.getCompletedAt()));
        assertTrue(storeResultTask.getCompletedAt().isAfter(generateJsonTask.getCompletedAt()));
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class DeterministicTaskExecutorConfig {
        @Bean("deterministicTaskExecutor")
        @Primary
        TaskExecutor deterministicTaskExecutor() {
            return task -> {
                try {
                    Thread.sleep(25L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new TaskExecutionResult(false, "Interrupted");
                }
                return new TaskExecutionResult(true, null);
            };
        }
    }
}
