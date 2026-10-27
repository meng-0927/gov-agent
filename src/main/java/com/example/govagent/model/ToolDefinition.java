package com.example.govagent.model;

import java.util.List;

public record ToolDefinition(
        String name,
        String description,
        List<ToolParameter> parameters
) {
}
