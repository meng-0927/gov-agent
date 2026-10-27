package com.example.govagent.planner;

import com.example.govagent.exception.AgentException;
import com.example.govagent.llm.LlmClient;
import com.example.govagent.model.AgentContext;
import com.example.govagent.model.AgentPlan;
import com.example.govagent.model.ConversationMessage;
import com.example.govagent.model.PlanStep;
import com.example.govagent.model.ToolDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于大模型的 Planner 实现。
 * 核心职责是把“自然语言请求 + 可用 Tool 描述 + 最近会话”转换成结构化 AgentPlan。
 */
@Component
public class LlmPlanner implements Planner {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final int maxRepairAttempts;

    public LlmPlanner(LlmClient llmClient,
                      ObjectMapper objectMapper,
                      @Value("${agent.planner.max-repair-attempts:2}") int maxRepairAttempts) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.maxRepairAttempts = maxRepairAttempts;
    }

    @Override
    public AgentPlan plan(AgentContext context, List<ToolDefinition> availableTools) {
        // Planner Prompt 中会注入 Tool schema 和最近会话，让模型只负责规划调用步骤。
        String prompt = buildPrompt(context, availableTools);
        String lastRaw = null;
        Exception lastError = null;

        // 模型输出 JSON 可能不稳定，因此允许有限次数“修复式重试”，而不是直接信任第一次输出。
        for (int attempt = 1; attempt <= maxRepairAttempts; attempt++) {
            try {
                String currentPrompt = attempt == 1
                        ? prompt
                        : prompt + "\n上一次输出无法解析，请严格输出符合 schema 的 JSON。"
                            + "\n上一次输出：" + lastRaw
                            + "\n错误：" + (lastError == null ? "unknown" : lastError.getMessage());

                // 先拿到模型原始 JSON，再反序列化成 Java 对象，最后做结构校验。
                lastRaw = llmClient.createPlan(context.getUserMessage(), currentPrompt);
                AgentPlan plan = objectMapper.readValue(lastRaw, AgentPlan.class);
                validatePlanShape(plan);
                return plan;
            } catch (JsonProcessingException | IllegalArgumentException ex) {
                lastError = ex;
            }
        }

        // 多次修复仍失败时，不让整个请求直接中断，退回到一条固定的兜底计划。
        return fallbackPlan(context.getUserMessage());
    }

    /**
     * 构造 Planner Prompt。这里把 Tool 定义序列化后交给模型，相当于告诉模型“系统允许调用哪些能力”。
     */
    private String buildPrompt(AgentContext context, List<ToolDefinition> tools) {
        try {
            List<String> history = context.getHistory().stream()
                    .map(this::formatMessage)
                    .toList();
            return """
                    你是政务智能助手的 Planner，只负责决定调用哪些 Tool，不执行 Tool。
                    必须只输出 JSON，不要 Markdown，不要解释。

                    输出 schema：
                    {
                      "steps": [
                        {
                          "id": "step1",
                          "tool": "tool_name",
                          "arguments": {"param": "value"},
                          "dependsOn": []
                        }
                      ]
                    }

                    可以使用完整占位符引用前序结果：
                    ${step1.output}
                    ${step1.data.someKey}

                    可用 Tools：
                    %s

                    最近会话：
                    %s

                    当前用户请求：
                    %s
                    """.formatted(
                    objectMapper.writeValueAsString(tools),
                    objectMapper.writeValueAsString(history),
                    context.getUserMessage());
        } catch (JsonProcessingException ex) {
            throw new AgentException("Failed to build planner prompt", ex);
        }
    }

    /**
     * 只校验 Plan 的基础结构是否合法；Tool 是否存在、参数类型是否正确由执行阶段继续校验。
     */
    private void validatePlanShape(AgentPlan plan) {
        if (plan.steps().isEmpty()) {
            throw new IllegalArgumentException("Plan contains no steps");
        }
        if (plan.steps().size() > 8) {
            throw new IllegalArgumentException("Plan has too many steps");
        }

        Set<String> seen = new HashSet<>();
        for (PlanStep step : plan.steps()) {
            if (step.id() == null || step.id().isBlank()) {
                throw new IllegalArgumentException("Step id is blank");
            }
            if (step.tool() == null || step.tool().isBlank()) {
                throw new IllegalArgumentException("Tool name is blank");
            }
            if (!seen.add(step.id())) {
                throw new IllegalArgumentException("Duplicate step id: " + step.id());
            }
        }
    }

    /**
     * Planner 自身解析失败时的固定兜底路径。
     * 注意：这是 Demo 中的 policy_search + final_answer 方案，当前真实项目复习时不必重点关注 FinalAnswerTool。
     */
    private AgentPlan fallbackPlan(String question) {
        return new AgentPlan(List.of(
                new PlanStep("fallback_search", "policy_search", java.util.Map.of("query", question), List.of()),
                new PlanStep("fallback_answer", "final_answer",
                        java.util.Map.of(
                                "question", question,
                                "references", "${fallback_search.data.documents}"),
                        List.of("fallback_search"))
        ));
    }

    private String formatMessage(ConversationMessage message) {
        return message.role() + ": " + message.content();
    }
}
