package com.aeries.documenttaskrunner.repository;

import com.aeries.documenttaskrunner.enums.TaskStatus;
import com.aeries.documenttaskrunner.model.Task;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByDocumentId(Long documentId);

    List<Task> findByStatusIn(List<TaskStatus> statuses);

    @Query("select t from Task t where t.status in :statuses and (t.nextRunAt is null or t.nextRunAt <= :now) order by t.createdAt asc")
    List<Task> findReadyTasks(List<TaskStatus> statuses, LocalDateTime now);

    List<Task> findByStatus(TaskStatus status);

    long countByStatus(TaskStatus status);

    boolean existsByNameAndDocumentId(String name, Long documentId);
}
