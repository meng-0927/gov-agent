package com.example.govagent.model;

public record ExecutionTrace(
        String stepId,
        String tool,
        TraceStatus status,
        int attempt,
        long durationMs,
        String message
) {
}
