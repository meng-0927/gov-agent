package com.example.govagent.service;

import com.example.govagent.conversation.ConversationService;
import com.example.govagent.executor.AgentExecutor;
import com.example.govagent.model.AgentContext;
import com.example.govagent.model.AgentExecutionResult;
import com.example.govagent.model.AgentPlan;
import com.example.govagent.model.AgentRunRecord;
import com.example.govagent.model.ChatRequest;
import com.example.govagent.model.ChatResponse;
import com.example.govagent.model.ConversationMessage;
import com.example.govagent.planner.Planner;
import com.example.govagent.tool.ToolRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agent 应用层总入口，负责串起一次请求的完整生命周期。
 * 本身不负责具体 Tool 业务，只负责“加载上下文 -> 生成计划 -> 执行计划 -> 保存结果”。
 */
@Service
public class AgentService {
    private final ConversationService conversationService;
    private final Planner planner;
    private final ToolRegistry toolRegistry;
    private final AgentExecutor executor;

    public AgentService(ConversationService conversationService,
                        Planner planner,
                        ToolRegistry toolRegistry,
                        AgentExecutor executor) {
        this.conversationService = conversationService;
        this.planner = planner;
        this.toolRegistry = toolRegistry;
        this.executor = executor;
    }

    public ChatResponse chat(ChatRequest request) {
        String sessionId = normalizeSessionId(request.sessionId());

        // 1. 加载最近会话。真实项目中优先从 Redis 取，未命中再回源 MySQL。
        List<ConversationMessage> history = conversationService.loadRecent(sessionId);

        // 2. 构造本次请求独立的 AgentContext，后续各个 Tool 的结果都会写入这里。
        AgentContext context = new AgentContext(
                request.userId(), sessionId, request.message(), history);

        // 3. Planner 只让模型“做决策”：选择 Tool、生成参数和依赖关系，不直接执行任何 Java 方法。
        AgentPlan plan = planner.plan(context, toolRegistry.definitions());

        // 4. 真正的执行权限在 Java 侧：Executor 会校验 Plan、解析参数、调用 Tool，并处理重试/降级。
        AgentExecutionResult result = executor.execute(plan, context);

        // 5. 保存用户消息、最终回答和执行轨迹，供后续多轮上下文、审计和问题排查使用。
        conversationService.append(sessionId, ConversationMessage.user(request.message()));
        conversationService.append(sessionId, ConversationMessage.assistant(result.finalAnswer()));

        conversationService.saveRun(new AgentRunRecord(
                sessionId,
                request.message(),
                result.finalAnswer(),
                result.trace(),
                Instant.now()));

        return new ChatResponse(
                sessionId,
                result.finalAnswer(),
                result.citations(),
                result.trace(),
                result.fallbackUsed());
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }
}
