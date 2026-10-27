package com.example.govagent.model;

import java.util.List;

public record AgentPlan(
        List<PlanStep> steps
) {
    public AgentPlan {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
