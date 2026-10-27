package com.example.govagent.tool;

import com.example.govagent.exception.ToolValidationException;
import com.example.govagent.model.ToolDefinition;
import com.example.govagent.model.ToolParamType;
import com.example.govagent.model.ToolParameter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tool 注册中心。Spring 会自动注入所有 Tool 实现类，因此新增能力只需要实现 Tool 并加 @Component。
 * Executor 通过 Registry 按名称查找 Tool，同时在真正调用前完成参数 schema 校验。
 */
@Component
public class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        // 启动时把所有 Tool Bean 建成 name -> Tool 映射，并阻止重名能力覆盖。
        for (Tool tool : toolList) {
            String name = tool.definition().name();
            if (tools.containsKey(name)) {
                throw new IllegalStateException("Duplicate tool name: " + name);
            }
            tools.put(name, tool);
        }
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Tool getRequired(String name) {
        // 模型只能调用白名单中已经注册的 Tool，无法凭空构造系统内部能力。
        return find(name).orElseThrow(() -> new ToolValidationException("Unknown tool: " + name));
    }

    public List<ToolDefinition> definitions() {
        // Planner 看到的是 ToolDefinition，而不是具体 Java 实现，从而只暴露受控的能力描述和参数。
        return tools.values().stream().map(Tool::definition).toList();
    }

    public void validateArguments(ToolDefinition definition, Map<String, Object> arguments) {
        // Java 侧再次校验必填项和参数类型，不能因为参数来自 LLM 就直接信任。
        for (ToolParameter parameter : definition.parameters()) {
            Object value = arguments.get(parameter.name());
            if (parameter.required() && isMissing(value)) {
                throw new ToolValidationException(
                        "Tool " + definition.name() + " missing required parameter: " + parameter.name());
            }
            if (value != null && !matches(parameter.type(), value)) {
                throw new ToolValidationException(
                        "Tool " + definition.name() + " parameter " + parameter.name()
                                + " expected " + parameter.type() + " but got " + value.getClass().getSimpleName());
            }
        }
    }

    private boolean isMissing(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    private boolean matches(ToolParamType type, Object value) {
        return switch (type) {
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case OBJECT -> value instanceof Map<?, ?>;
            case ARRAY -> value instanceof List<?>;
        };
    }
}
