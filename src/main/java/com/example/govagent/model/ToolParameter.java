package com.example.govagent.model;

public record ToolParameter(
        String name,
        ToolParamType type,
        String description,
        boolean required
) {
}
