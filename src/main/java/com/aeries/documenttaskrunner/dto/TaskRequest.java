package com.aeries.documenttaskrunner.dto;

import com.aeries.documenttaskrunner.enums.TaskType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class TaskRequest {
    @NotBlank
    private String name;

    @NotNull
    private TaskType type;

    private List<String> dependencies = new ArrayList<>();

    @Min(1)
    private int maxAttempts = 3;

    @Min(0)
    @Max(1)
    private double failureProbability = 0.1;

    private int durationMinMs = 1000;
    private int durationMaxMs = 3000;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaskType getType() {
        return type;
    }

    public void setType(TaskType type) {
        this.type = type;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies == null ? new ArrayList<>() : dependencies;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public double getFailureProbability() {
        return failureProbability;
    }

    public void setFailureProbability(double failureProbability) {
        this.failureProbability = failureProbability;
    }

    public int getDurationMinMs() {
        return durationMinMs;
    }

    public void setDurationMinMs(int durationMinMs) {
        this.durationMinMs = durationMinMs;
    }

    public int getDurationMaxMs() {
        return durationMaxMs;
    }

    public void setDurationMaxMs(int durationMaxMs) {
        this.durationMaxMs = durationMaxMs;
    }
}
