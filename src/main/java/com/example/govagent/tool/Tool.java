package com.example.govagent.tool;

import com.example.govagent.model.AgentContext;
import com.example.govagent.model.ToolDefinition;
import com.example.govagent.model.ToolResult;

import java.util.Map;

public interface Tool {
    ToolDefinition definition();

    ToolResult execute(AgentContext context, Map<String, Object> arguments);
}
