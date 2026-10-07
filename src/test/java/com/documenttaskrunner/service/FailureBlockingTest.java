package com.documenttaskrunner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.documenttaskrunner.dependency.DependencyService;
import com.documenttaskrunner.enums.TaskStatus;
import com.documenttaskrunner.model.Task;
import com.documenttaskrunner.model.TaskDependency;
import com.documenttaskrunner.repository.TaskDependencyRepository;
import com.documenttaskrunner.repository.TaskRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FailureBlockingTest {
    @Test
    void failedDependencyBlocksDependentsRecursively() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskDependencyRepository dependencyRepository = mock(TaskDependencyRepository.class);
        DependencyService dependencyService = new DependencyService(taskRepository, dependencyRepository);

        Map<Long, Task> tasks = new HashMap<>();
        Task a = task("A", 1L, TaskStatus.FAILED);
        Task b = task("B", 2L, TaskStatus.WAITING);
        Task c = task("C", 3L, TaskStatus.WAITING);
        Task d = task("D", 4L, TaskStatus.WAITING);
        tasks.put(1L, a); tasks.put(2L, b); tasks.put(3L, c); tasks.put(4L, d);

        when(taskRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(tasks.get(inv.getArgument(0))));
        when(taskRepository.save(org.mockito.ArgumentMatchers.any(Task.class))).thenAnswer(inv -> {
            Task task = inv.getArgument(0);
            tasks.put(task.getId(), task);
            return task;
        });
        when(dependencyRepository.findByDependsOnTaskId(1L)).thenReturn(List.of(
                new TaskDependency(2L, 1L),
                new TaskDependency(3L, 1L)
        ));
        when(dependencyRepository.findByDependsOnTaskId(2L)).thenReturn(List.of(new TaskDependency(4L, 2L)));
        when(dependencyRepository.findByDependsOnTaskId(3L)).thenReturn(List.of(new TaskDependency(4L, 3L)));
        when(dependencyRepository.findByTaskId(2L)).thenReturn(List.of(new TaskDependency(2L, 1L)));
        when(dependencyRepository.findByTaskId(3L)).thenReturn(List.of(new TaskDependency(3L, 1L)));
        when(dependencyRepository.findByTaskId(4L)).thenReturn(List.of(
                new TaskDependency(4L, 2L),
                new TaskDependency(4L, 3L)
        ));

        dependencyService.reEvaluateDependents(1L);

        assertEquals(TaskStatus.BLOCKED, b.getStatus());
        assertEquals(TaskStatus.BLOCKED, c.getStatus());
        assertEquals(TaskStatus.BLOCKED, d.getStatus());
    }

    private Task task(String name, Long id, TaskStatus status) {
        Task task = new Task();
        task.setId(id);
        task.setName(name);
        task.setStatus(status);
        return task;
    }
}
