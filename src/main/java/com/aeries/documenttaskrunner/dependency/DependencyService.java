package com.aeries.documenttaskrunner.dependency;

import com.aeries.documenttaskrunner.enums.TaskStatus;
import com.aeries.documenttaskrunner.model.Task;
import com.aeries.documenttaskrunner.model.TaskDependency;
import com.aeries.documenttaskrunner.repository.TaskDependencyRepository;
import com.aeries.documenttaskrunner.repository.TaskRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DependencyService {
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;

    public DependencyService(TaskRepository taskRepository, TaskDependencyRepository dependencyRepository) {
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
    }

    public boolean allDependenciesSucceeded(Task task) {
        List<TaskDependency> dependencies = dependencyRepository.findByTaskId(task.getId());
        for (TaskDependency dependency : dependencies) {
            Task parent = taskRepository.findById(dependency.getDependsOnTaskId()).orElse(null);
            if (parent == null || parent.getStatus() != TaskStatus.SUCCEEDED) {
                return false;
            }
        }
        return true;
    }

    public boolean hasPermanentFailureDependency(Task task) {
        List<TaskDependency> dependencies = dependencyRepository.findByTaskId(task.getId());
        for (TaskDependency dependency : dependencies) {
            Task parent = taskRepository.findById(dependency.getDependsOnTaskId()).orElse(null);
            if (parent != null && (parent.getStatus() == TaskStatus.FAILED || parent.getStatus() == TaskStatus.BLOCKED || parent.getStatus() == TaskStatus.CANCELLED)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void reEvaluateDependents(Long sourceTaskId) {
        List<TaskDependency> incoming = dependencyRepository.findByDependsOnTaskId(sourceTaskId);
        List<Long> dependentIds = incoming.stream().map(TaskDependency::getTaskId).distinct().toList();

        for (Long dependentId : dependentIds) {
            Task task = taskRepository.findById(dependentId).orElse(null);
            if (task == null || task.getStatus().equals(TaskStatus.SUCCEEDED)
                    || task.getStatus().equals(TaskStatus.FAILED)
                    || task.getStatus().equals(TaskStatus.CANCELLED)
                    || task.getStatus().equals(TaskStatus.BLOCKED)) {
                continue;
            }

            boolean blocked = false;
            String reason = null;
            List<TaskDependency> dependencies = dependencyRepository.findByTaskId(task.getId());
            for (TaskDependency dependency : dependencies) {
                Task dependencyTask = taskRepository.findById(dependency.getDependsOnTaskId()).orElse(null);
                if (dependencyTask == null) {
                    blocked = true;
                    reason = "Dependency is missing";
                    break;
                }
                if (dependencyTask.getStatus() == TaskStatus.FAILED
                        || dependencyTask.getStatus() == TaskStatus.BLOCKED
                        || dependencyTask.getStatus() == TaskStatus.CANCELLED) {
                    blocked = true;
                    reason = "Required dependency " + dependencyTask.getName() + " is " + dependencyTask.getStatus();
                    break;
                }
            }

            if (blocked) {
                task.setStatus(TaskStatus.BLOCKED);
                task.setCompletedAt(LocalDateTime.now());
                task.setErrorMessage(reason);
                task.setNextRunAt(null);
                taskRepository.save(task);
                reEvaluateDependents(task.getId());
                continue;
            }

            if (allDependenciesSucceeded(task) && (task.getStatus() == TaskStatus.WAITING || task.getStatus() == TaskStatus.RETRY_WAITING)) {
                task.setStatus(TaskStatus.WAITING);
                task.setErrorMessage(null);
                task.setNextRunAt(null);
                taskRepository.save(task);
            }
        }
    }

    public Map<Long, List<Long>> buildDependencyMap(List<Task> tasks) {
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        List<TaskDependency> dependencies = dependencyRepository.findByTaskIdIn(taskIds);

        Map<Long, List<Long>> results = tasks.stream().collect(Collectors.toMap(Task::getId, ignored -> new ArrayList<>()));
        for (TaskDependency dependency : dependencies) {
            results.computeIfAbsent(dependency.getTaskId(), key -> new ArrayList<>()).add(dependency.getDependsOnTaskId());
        }
        return results;
    }
}
