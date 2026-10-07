package com.documenttaskrunner.executor;

import com.documenttaskrunner.config.TaskRunnerProperties;
import com.documenttaskrunner.model.Task;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class SimulatedTaskExecutor implements TaskExecutor {
    private final TaskRunnerProperties properties;

    public SimulatedTaskExecutor(TaskRunnerProperties properties) {
        this.properties = properties;
    }

    @Override
    public TaskExecutionResult execute(Task task) {
        int minMs = task.getDurationMinMs() > 0 ? task.getDurationMinMs() : properties.getExecution().getDefaultDurationMinMs();
        int maxMs = task.getDurationMaxMs() > 0 ? task.getDurationMaxMs() : properties.getExecution().getDefaultDurationMaxMs();
        if (maxMs < minMs) {
            maxMs = minMs;
        }

        int sleepMs = ThreadLocalRandom.current().nextInt(minMs, maxMs + 1);

        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TaskExecutionResult(false, "Task interrupted: " + e.getMessage());
        }

        double failureProbability = task.getFailureProbability();
        double randomValue = ThreadLocalRandom.current().nextDouble();
        if (randomValue < failureProbability) {
            return new TaskExecutionResult(false, "Simulated task failure for " + task.getName());
        }
        return new TaskExecutionResult(true, null);
    }
}
