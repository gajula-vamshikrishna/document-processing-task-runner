package com.aeries.documenttaskrunner.executor;

import com.aeries.documenttaskrunner.model.Task;

public interface TaskExecutor {
    TaskExecutionResult execute(Task task);
}
