package com.example.govagent.model;

import java.util.List;

public record AgentExecutionResult(
        String finalAnswer,
        List<Citation> citations,
        List<ExecutionTrace> trace,
        boolean fallbackUsed
) {
}
