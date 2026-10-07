package com.aeries.documenttaskrunner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "task-runner")
public class TaskRunnerProperties {
    private int maxConcurrency = 5;
    private Scheduler scheduler = new Scheduler();
    private Retry retry = new Retry();
    private Execution execution = new Execution();

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public Execution getExecution() {
        return execution;
    }

    public void setExecution(Execution execution) {
        this.execution = execution;
    }

    public static class Scheduler {
        private long pollingIntervalMs = 500;

        public long getPollingIntervalMs() {
            return pollingIntervalMs;
        }

        public void setPollingIntervalMs(long pollingIntervalMs) {
            this.pollingIntervalMs = pollingIntervalMs;
        }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private long initialDelayMs = 1000;
        private double multiplier = 2.0;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }
    }

    public static class Execution {
        private int defaultDurationMinMs = 1000;
        private int defaultDurationMaxMs = 3000;
        private int timeoutSeconds = 10;

        public int getDefaultDurationMinMs() {
            return defaultDurationMinMs;
        }

        public void setDefaultDurationMinMs(int defaultDurationMinMs) {
            this.defaultDurationMinMs = defaultDurationMinMs;
        }

        public int getDefaultDurationMaxMs() {
            return defaultDurationMaxMs;
        }

        public void setDefaultDurationMaxMs(int defaultDurationMaxMs) {
            this.defaultDurationMaxMs = defaultDurationMaxMs;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
