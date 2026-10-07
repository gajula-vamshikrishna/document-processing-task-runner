package com.aeries.documenttaskrunner.repository;

import com.aeries.documenttaskrunner.model.TaskDependency;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {
    List<TaskDependency> findByTaskId(Long taskId);

    List<TaskDependency> findByDependsOnTaskId(Long taskId);

    List<TaskDependency> findByTaskIdIn(List<Long> taskIds);
}
