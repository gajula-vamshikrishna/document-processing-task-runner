package com.aeries.documenttaskrunner.dto;

public record StatsResponse(long running, long waiting, long retryWaiting, long succeeded, long failed, long blocked, long cancelled) {
}
