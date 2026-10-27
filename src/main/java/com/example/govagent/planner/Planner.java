package com.example.govagent.planner;

import com.example.govagent.model.AgentContext;
import com.example.govagent.model.AgentPlan;
import com.example.govagent.model.ToolDefinition;

import java.util.List;

public interface Planner {
    AgentPlan plan(AgentContext context, List<ToolDefinition> availableTools);
}
