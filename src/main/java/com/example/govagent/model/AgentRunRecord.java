package com.example.govagent.model;

import java.time.Instant;
import java.util.List;

public record AgentRunRecord(
        String sessionId,
        String userMessage,
        String finalAnswer,
        List<ExecutionTrace> trace,
        Instant createdAt
) {
}
