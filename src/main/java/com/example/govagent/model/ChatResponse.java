package com.example.govagent.model;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String answer,
        List<Citation> citations,
        List<ExecutionTrace> trace,
        boolean fallbackUsed
) {
}
