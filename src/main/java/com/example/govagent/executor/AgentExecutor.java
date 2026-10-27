package com.example.govagent.executor;

import com.example.govagent.exception.AgentException;
import com.example.govagent.model.AgentContext;
import com.example.govagent.model.AgentExecutionResult;
import com.example.govagent.model.AgentPlan;
import com.example.govagent.model.Citation;
import com.example.govagent.model.ExecutionTrace;
import com.example.govagent.model.PlanStep;
import com.example.govagent.model.ToolResult;
import com.example.govagent.model.TraceStatus;
import com.example.govagent.tool.Tool;
import com.example.govagent.tool.ToolRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 的核心执行器，也是“模型决策、Java 执行”边界真正落地的地方。
 * 它不相信模型输出，会在执行前检查依赖、Tool、参数，并统一处理重试、降级和执行轨迹。
 */
@Component
public class AgentExecutor {
    private final ToolRegistry toolRegistry;
    private final ArgumentResolver argumentResolver;
    private final FallbackPlanFactory fallbackPlanFactory;
    private final int maxToolAttempts;

    public AgentExecutor(ToolRegistry toolRegistry,
                         ArgumentResolver argumentResolver,
                         FallbackPlanFactory fallbackPlanFactory,
                         @Value("${agent.executor.max-tool-attempts:2}") int maxToolAttempts) {
        this.toolRegistry = toolRegistry;
        this.argumentResolver = argumentResolver;
        this.fallbackPlanFactory = fallbackPlanFactory;
        this.maxToolAttempts = maxToolAttempts;
    }

    public AgentExecutionResult execute(AgentPlan plan, AgentContext context) {
        try {
            // 优先执行模型生成的主计划。
            return executePlan(plan, context, false);
        } catch (RuntimeException primaryFailure) {
            context.addTrace(new ExecutionTrace(
                    "fallback", "system", TraceStatus.FALLBACK, 0, 0,
                    "Primary plan failed: " + primaryFailure.getMessage()));

            try {
                // 主计划任何一步失败后，重新执行一条受控的固定兜底计划。
                AgentPlan fallback = fallbackPlanFactory.build(context.getUserMessage());
                return executePlan(fallback, context, true);
            } catch (RuntimeException fallbackFailure) {
                // 连兜底计划都失败时返回安全文案，保证接口仍能正常响应。
                String safeAnswer = "当前智能编排执行失败，请稍后重试；如涉及政策办理，请以正式政策原文和主管部门要求为准。";
                return new AgentExecutionResult(safeAnswer, collectCitations(context), context.getTraces(), true);
            }
        }
    }

    private AgentExecutionResult executePlan(AgentPlan plan, AgentContext context, boolean fallbackUsed) {
        // 当前 Executor 按 Plan 中的顺序串行执行，因此依赖步骤必须出现在当前步骤之前。
        validateDependencyOrder(plan);
        String finalAnswer = null;

        for (PlanStep step : plan.steps()) {
            // 依次完成：检查依赖 -> 从 Registry 找 Tool -> 解析前序结果占位符 -> 校验参数。
            ensureDependenciesSucceeded(step, context);
            Tool tool = toolRegistry.getRequired(step.tool());
            Map<String, Object> resolvedArgs = argumentResolver.resolve(step.arguments(), context);
            toolRegistry.validateArguments(tool.definition(), resolvedArgs);

            ToolResult result = executeWithRetry(step, tool, resolvedArgs, context);
            if (!result.success()) {
                throw new AgentException(
                        "Tool " + step.tool() + " failed after retry: " + result.errorMessage());
            }

            // 每一步结果都写入 AgentContext，后续步骤可通过 ${stepId.output/data.xxx} 引用。
            context.putStepResult(step.id(), result);
            if (result.output() != null && !result.output().isBlank()) {
                finalAnswer = result.output();
            }
        }

        if (finalAnswer == null) {
            throw new AgentException("Plan completed without final output");
        }

        return new AgentExecutionResult(finalAnswer, collectCitations(context), context.getTraces(), fallbackUsed);
    }

    private ToolResult executeWithRetry(PlanStep step,
                                        Tool tool,
                                        Map<String, Object> arguments,
                                        AgentContext context) {
        ToolResult lastResult = null;

        // Tool 级重试：仅对标记为 retryable 的失败重试，避免不可恢复错误被无限调用。
        for (int attempt = 1; attempt <= maxToolAttempts; attempt++) {
            long start = System.nanoTime();
            try {
                ToolResult result = tool.execute(context, arguments);
                long durationMs = (System.nanoTime() - start) / 1_000_000;

                if (result.success()) {
                    context.addTrace(new ExecutionTrace(
                            step.id(), step.tool(), TraceStatus.SUCCESS, attempt,
                            durationMs, "Tool executed successfully"));
                    return result;
                }

                lastResult = result;
                TraceStatus status = result.retryable() && attempt < maxToolAttempts
                        ? TraceStatus.RETRY : TraceStatus.FAILED;
                context.addTrace(new ExecutionTrace(
                        step.id(), step.tool(), status, attempt,
                        durationMs, result.errorMessage()));

                if (!result.retryable()) {
                    return result;
                }
            } catch (RuntimeException ex) {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                boolean canRetry = attempt < maxToolAttempts;
                context.addTrace(new ExecutionTrace(
                        step.id(), step.tool(), canRetry ? TraceStatus.RETRY : TraceStatus.FAILED,
                        attempt, durationMs, ex.getMessage()));
                lastResult = ToolResult.failure(ex.getMessage(), canRetry);
            }
        }

        return lastResult == null
                ? ToolResult.failure("Unknown tool failure", false)
                : lastResult;
    }

    /**
     * 校验 dependsOn 是否引用真实步骤，以及依赖是否出现在当前步骤之前。
     * 这里没有做 DAG 调度，而是用更简单、便于控制的顺序执行模型。
     */
    private void validateDependencyOrder(AgentPlan plan) {
        Map<String, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < plan.steps().size(); i++) {
            PlanStep step = plan.steps().get(i);
            if (order.put(step.id(), i) != null) {
                throw new AgentException("Duplicate step id: " + step.id());
            }
        }
        for (int i = 0; i < plan.steps().size(); i++) {
            PlanStep step = plan.steps().get(i);
            for (String dependency : step.dependsOn()) {
                Integer depIndex = order.get(dependency);
                if (depIndex == null) {
                    throw new AgentException("Unknown dependency " + dependency + " in " + step.id());
                }
                if (depIndex >= i) {
                    throw new AgentException("Dependency must appear before current step: " + dependency);
                }
            }
        }
    }

    private void ensureDependenciesSucceeded(PlanStep step, AgentContext context) {
        for (String dependency : step.dependsOn()) {
            ToolResult result = context.getStepResult(dependency);
            if (result == null || !result.success()) {
                throw new AgentException("Dependency not completed successfully: " + dependency);
            }
        }
    }

    /**
     * 汇总所有 Tool 返回的引用，并按 documentId + chunkId 去重，最终统一随回答返回。
     */
    private List<Citation> collectCitations(AgentContext context) {
        Map<String, Citation> unique = new LinkedHashMap<>();
        context.getStepResults().values().stream()
                .flatMap(result -> result.citations().stream())
                .forEach(citation -> unique.put(
                        citation.documentId() + ":" + citation.chunkId(), citation));
        return new ArrayList<>(unique.values());
    }
}
