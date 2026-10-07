package com.documenttaskrunner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.documenttaskrunner.dependency.CycleDetector;
import com.documenttaskrunner.dto.DocumentRequest;
import com.documenttaskrunner.dto.SubmitWorkflowRequest;
import com.documenttaskrunner.dto.TaskRequest;
import com.documenttaskrunner.dto.WorkflowSubmissionResponse;
import com.documenttaskrunner.exception.CycleDetectedException;
import com.documenttaskrunner.exception.UnknownDependencyException;
import com.documenttaskrunner.model.Document;
import com.documenttaskrunner.model.Task;
import com.documenttaskrunner.repository.DocumentRepository;
import com.documenttaskrunner.repository.TaskDependencyRepository;
import com.documenttaskrunner.repository.TaskRepository;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WorkflowServiceTest {
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final TaskDependencyRepository dependencyRepository = mock(TaskDependencyRepository.class);
    private final CycleDetector cycleDetector = new CycleDetector();
    private final WorkflowService workflowService = new WorkflowService(documentRepository, taskRepository, dependencyRepository, cycleDetector);
    private final AtomicLong idSequence = new AtomicLong(1L);

    @Test
    void validWorkflowAccepted() {
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document document = inv.getArgument(0);
            document.setId(101L);
            return document;
        });
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task task = inv.getArgument(0);
            task.setId(idSequence.getAndIncrement());
            return task;
        });

        SubmitWorkflowRequest request = new SubmitWorkflowRequest();
        DocumentRequest documentRequest = new DocumentRequest();
        documentRequest.setFileName("hotel_bill_1025.pdf");
        request.setDocument(documentRequest);

        TaskRequest validate = new TaskRequest();
        validate.setName("validate-document");
        validate.setDependencies(java.util.List.of());

        TaskRequest extract = new TaskRequest();
        extract.setName("extract-data");
        extract.setDependencies(java.util.List.of("validate-document"));

        TaskRequest classify = new TaskRequest();
        classify.setName("classify-document");
        classify.setDependencies(java.util.List.of("validate-document"));

        request.setTasks(java.util.List.of(validate, extract, classify));

        WorkflowSubmissionResponse response = workflowService.submitWorkflow(request);

        assertEquals(101L, response.documentId());
        assertEquals(3, response.taskIds().size());
    }

    @Test
    void unknownDependencyRejected() {
        SubmitWorkflowRequest request = new SubmitWorkflowRequest();
        DocumentRequest documentRequest = new DocumentRequest();
        documentRequest.setFileName("hotel_bill_1025.pdf");
        request.setDocument(documentRequest);

        TaskRequest task = new TaskRequest();
        task.setName("extract-data");
        task.setDependencies(java.util.List.of("missing-task"));
        request.setTasks(java.util.List.of(task));

        assertThrows(UnknownDependencyException.class, () -> workflowService.submitWorkflow(request));
    }

    @Test
    void duplicateTaskNameRejected() {
        SubmitWorkflowRequest request = new SubmitWorkflowRequest();
        DocumentRequest documentRequest = new DocumentRequest();
        documentRequest.setFileName("hotel_bill_1025.pdf");
        request.setDocument(documentRequest);

        TaskRequest first = new TaskRequest();
        first.setName("duplicate");
        first.setDependencies(java.util.List.of());

        TaskRequest second = new TaskRequest();
        second.setName("duplicate");
        second.setDependencies(java.util.List.of());

        request.setTasks(java.util.List.of(first, second));

        assertThrows(IllegalArgumentException.class, () -> workflowService.submitWorkflow(request));
    }

    @Test
    void circularWorkflowRejected() {
        SubmitWorkflowRequest request = new SubmitWorkflowRequest();
        DocumentRequest documentRequest = new DocumentRequest();
        documentRequest.setFileName("hotel_bill_1025.pdf");
        request.setDocument(documentRequest);

        TaskRequest first = new TaskRequest();
        first.setName("A");
        first.setDependencies(java.util.List.of("B"));

        TaskRequest second = new TaskRequest();
        second.setName("B");
        second.setDependencies(java.util.List.of("A"));

        request.setTasks(java.util.List.of(first, second));

        assertThrows(CycleDetectedException.class, () -> workflowService.submitWorkflow(request));
    }
}
