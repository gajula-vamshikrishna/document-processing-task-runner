package com.documenttaskrunner.executor;

import com.documenttaskrunner.model.Task;

public interface TaskExecutor {
    TaskExecutionResult execute(Task task);
}
