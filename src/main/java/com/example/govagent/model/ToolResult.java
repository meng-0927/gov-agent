package com.example.govagent.model;

import java.util.List;
import java.util.Map;

public record ToolResult(
        boolean success,
        String output,
        Map<String, Object> data,
        List<Citation> citations,
        boolean retryable,
        String errorMessage
) {
    public ToolResult {
        data = data == null ? Map.of() : Map.copyOf(data);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public static ToolResult success(String output, Map<String, Object> data, List<Citation> citations) {
        return new ToolResult(true, output, data, citations, false, null);
    }

    public static ToolResult success(String output) {
        return success(output, Map.of(), List.of());
    }

    public static ToolResult failure(String message, boolean retryable) {
        return new ToolResult(false, null, Map.of(), List.of(), retryable, message);
    }
}
