package com.example.govagent.controller;

import com.example.govagent.model.ChatRequest;
import com.example.govagent.model.ChatResponse;
import com.example.govagent.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 对外 HTTP 入口。
 * Controller 只负责接收请求并交给 AgentService，具体的规划、Tool 调用和会话管理都不放在这一层。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 一次完整 Agent 对话的入口。@Valid 先完成基础参数校验，再进入业务编排。
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return agentService.chat(request);
    }
}
