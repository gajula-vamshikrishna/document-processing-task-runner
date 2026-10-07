package com.aeries.documenttaskrunner.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aeries.documenttaskrunner.config.TaskRunnerProperties;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {
    @Test
    void calculatesIncreasingBackoff() {
        TaskRunnerProperties properties = new TaskRunnerProperties();
        properties.getRetry().setInitialDelayMs(1000L);
        properties.getRetry().setMultiplier(2.0);

        RetryPolicy policy = new RetryPolicy(properties);

        assertEquals(1000L, policy.delayForAttempt(1));
        assertEquals(2000L, policy.delayForAttempt(2));
        assertEquals(4000L, policy.delayForAttempt(3));
    }

    @Test
    void stopRetryingAtMaxAttempts() {
        TaskRunnerProperties properties = new TaskRunnerProperties();
        RetryPolicy policy = new RetryPolicy(properties);

        assertTrue(policy.shouldRetry(1, 3));
        assertTrue(policy.shouldRetry(2, 3));
        assertFalse(policy.shouldRetry(3, 3));
    }
}
