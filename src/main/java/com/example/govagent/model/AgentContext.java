package com.example.govagent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-request runtime context. It is intentionally mutable because Tool execution
 * writes intermediate results here for later steps to consume.
 */
public class AgentContext {
    private final String userId;
    private final String sessionId;
    private final String userMessage;
    private final List<ConversationMessage> history;
    private final Map<String, ToolResult> stepResults = new LinkedHashMap<>();
    private final List<ExecutionTrace> traces = new ArrayList<>();

    public AgentContext(String userId,
                        String sessionId,
                        String userMessage,
                        List<ConversationMessage> history) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.history = history == null ? List.of() : List.copyOf(history);
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public List<ConversationMessage> getHistory() {
        return history;
    }

    public void putStepResult(String stepId, ToolResult result) {
        stepResults.put(stepId, result);
    }

    public ToolResult getStepResult(String stepId) {
        return stepResults.get(stepId);
    }

    public Map<String, ToolResult> getStepResults() {
        return Collections.unmodifiableMap(stepResults);
    }

    public void addTrace(ExecutionTrace trace) {
        traces.add(trace);
    }

    public List<ExecutionTrace> getTraces() {
        return List.copyOf(traces);
    }
}
