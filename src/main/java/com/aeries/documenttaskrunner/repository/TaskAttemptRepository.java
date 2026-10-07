package com.aeries.documenttaskrunner.repository;

import com.aeries.documenttaskrunner.model.TaskAttempt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAttemptRepository extends JpaRepository<TaskAttempt, Long> {
    Optional<TaskAttempt> findByTaskIdAndAttemptNumber(Long taskId, int attemptNumber);
}
