package com.aeries.documenttaskrunner.service;

import com.aeries.documenttaskrunner.dependency.CycleDetector;
import com.aeries.documenttaskrunner.dto.SubmitWorkflowRequest;
import com.aeries.documenttaskrunner.dto.TaskRequest;
import com.aeries.documenttaskrunner.dto.WorkflowSubmissionResponse;
import com.aeries.documenttaskrunner.enums.TaskStatus;
import com.aeries.documenttaskrunner.exception.CycleDetectedException;
import com.aeries.documenttaskrunner.exception.UnknownDependencyException;
import com.aeries.documenttaskrunner.model.Document;
import com.aeries.documenttaskrunner.model.Task;
import com.aeries.documenttaskrunner.model.TaskDependency;
import com.aeries.documenttaskrunner.repository.DocumentRepository;
import com.aeries.documenttaskrunner.repository.TaskDependencyRepository;
import com.aeries.documenttaskrunner.repository.TaskRepository;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {
    private final DocumentRepository documentRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final CycleDetector cycleDetector;

    public WorkflowService(DocumentRepository documentRepository,
                           TaskRepository taskRepository,
                           TaskDependencyRepository taskDependencyRepository,
                           CycleDetector cycleDetector) {
        this.documentRepository = documentRepository;
        this.taskRepository = taskRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.cycleDetector = cycleDetector;
    }

    @Transactional
    public WorkflowSubmissionResponse submitWorkflow(@Valid SubmitWorkflowRequest request) {
        if (request == null || request.getTasks() == null || request.getTasks().isEmpty()) {
            throw new IllegalArgumentException("Workflow must contain at least one task");
        }

        if (request.getDocument() == null || request.getDocument().getFileName() == null || request.getDocument().getFileName().isBlank()) {
            throw new IllegalArgumentException("Document file name must be provided");
        }

        Map<String, Integer> taskNameCounts = new HashMap<>();
        for (TaskRequest taskRequest : request.getTasks()) {
            if (taskRequest.getName() == null || taskRequest.getName().isBlank()) {
                throw new IllegalArgumentException("Task name cannot be blank");
            }
            String normalized = taskRequest.getName().trim();
            taskNameCounts.merge(normalized, 1, Integer::sum);
            if (taskNameCounts.get(normalized) > 1) {
                throw new IllegalArgumentException("Duplicate task name in workflow: " + normalized);
            }
        }

        Map<String, TaskRequest> taskByName = new LinkedHashMap<>();
        for (TaskRequest taskRequest : request.getTasks()) {
            taskByName.put(taskRequest.getName().trim(), taskRequest);
        }

        for (TaskRequest taskRequest : request.getTasks()) {
            for (String dependencyName : taskRequest.getDependencies()) {
                if (!taskByName.containsKey(dependencyName)) {
                    throw new UnknownDependencyException("Unknown dependency '" + dependencyName + "' referenced by task '" + taskRequest.getName() + "'");
                }
            }
        }

        Map<String, List<String>> graph = new HashMap<>();
        for (TaskRequest taskRequest : request.getTasks()) {
            graph.put(taskRequest.getName().trim(), new ArrayList<>(taskRequest.getDependencies()));
        }
        List<String> cycle = cycleDetector.detectCycle(graph);
        if (!cycle.isEmpty()) {
            throw new CycleDetectedException("Workflow contains a circular dependency: " + String.join(" -> ", cycle));
        }

        Document document = new Document(request.getDocument().getFileName().trim());
        documentRepository.save(document);

        Map<String, Task> savedTasks = new LinkedHashMap<>();
        for (TaskRequest taskRequest : request.getTasks()) {
            Task task = new Task();
            task.setDocument(document);
            task.setName(taskRequest.getName().trim());
            task.setType(taskRequest.getType());
            task.setStatus(TaskStatus.WAITING);
            task.setMaxAttempts(taskRequest.getMaxAttempts() > 0 ? taskRequest.getMaxAttempts() : 3);
            task.setFailureProbability(taskRequest.getFailureProbability() >= 0 && taskRequest.getFailureProbability() <= 1 ? taskRequest.getFailureProbability() : 0.1);
            task.setDurationMinMs(taskRequest.getDurationMinMs() > 0 ? taskRequest.getDurationMinMs() : 1000);
            task.setDurationMaxMs(Math.max(taskRequest.getDurationMaxMs(), taskRequest.getDurationMinMs()));
            taskRepository.save(task);
            savedTasks.put(task.getName(), task);
        }

        for (TaskRequest taskRequest : request.getTasks()) {
            Task task = savedTasks.get(taskRequest.getName().trim());
            for (String dependencyName : taskRequest.getDependencies()) {
                Task dependencyTask = savedTasks.get(dependencyName);
                if (dependencyTask != null) {
                    taskDependencyRepository.save(new TaskDependency(task.getId(), dependencyTask.getId()));
                }
            }
        }

        return new WorkflowSubmissionResponse(document.getId(), savedTasks.values().stream().map(Task::getId).toList());
    }
}
