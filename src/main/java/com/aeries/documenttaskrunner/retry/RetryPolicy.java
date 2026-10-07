package com.aeries.documenttaskrunner.retry;

import com.aeries.documenttaskrunner.config.TaskRunnerProperties;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {
    private final TaskRunnerProperties properties;

    public RetryPolicy(TaskRunnerProperties properties) {
        this.properties = properties;
    }

    public long delayForAttempt(int attemptNumber) {
        long initialDelay = properties.getRetry().getInitialDelayMs();
        double multiplier = properties.getRetry().getMultiplier();
        if (attemptNumber <= 1) {
            return initialDelay;
        }
        return Math.round(initialDelay * Math.pow(multiplier, Math.max(0, attemptNumber - 1)));
    }

    public boolean shouldRetry(int attemptCount, int maxAttempts) {
        return attemptCount < maxAttempts;
    }
}
