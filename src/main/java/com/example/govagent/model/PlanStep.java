package com.example.govagent.model;

import java.util.List;
import java.util.Map;

public record PlanStep(
        String id,
        String tool,
        Map<String, Object> arguments,
        List<String> dependsOn
) {
    public PlanStep {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
