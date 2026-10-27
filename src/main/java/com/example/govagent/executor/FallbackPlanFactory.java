package com.example.govagent.executor;

import com.example.govagent.model.AgentPlan;
import com.example.govagent.model.PlanStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Executor 层的兜底计划工厂。主 Plan 执行失败时，使用一条受控、固定的调用链继续尝试响应用户。
 * 当前内容是 Demo 方案，主要用于展示“主链路失败 -> 固定降级链路”的机制。
 */
@Component
public class FallbackPlanFactory {
    public AgentPlan build(String question) {
        return new AgentPlan(List.of(
                new PlanStep("fallback1", "policy_search", Map.of("query", question), List.of()),
                new PlanStep("fallback2", "final_answer",
                        Map.of(
                                "question", question,
                                "references", "${fallback1.data.documents}"),
                        List.of("fallback1"))
        ));
    }
}
