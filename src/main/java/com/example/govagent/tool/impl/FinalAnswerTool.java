package com.example.govagent.tool.impl;

import com.example.govagent.llm.LlmClient;
import com.example.govagent.model.AgentContext;
import com.example.govagent.model.ToolDefinition;
import com.example.govagent.model.ToolParamType;
import com.example.govagent.model.ToolParameter;
import com.example.govagent.model.ToolResult;
import com.example.govagent.tool.Tool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Demo 中把“基于检索材料生成最终回答”单独拆成了一个 Tool。
 * 按你最终确认的真实项目架构，知识问答本身可以封装成完整 RAG Tool，因此这个类复习时不必作为重点。
 */
@Component
public class FinalAnswerTool implements Tool {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public FinalAnswerTool(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "final_answer",
                "根据检索材料生成面向用户的最终回答，要求仅依据给定材料回答。",
                List.of(
                        new ToolParameter("question", ToolParamType.STRING, "用户问题", true),
                        new ToolParameter("references", ToolParamType.ARRAY, "检索得到的材料列表", true)
                )
        );
    }

    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> arguments) {
        String question = (String) arguments.get("question");
        // references 是前序检索步骤的结构化结果，而不是让模型再次自行检索。
        Object references = arguments.get("references");
        try {
            String prompt = """
                    你是政务问答助手。只能根据给定材料回答，不得编造。
                    用户问题：%s
                    参考材料：%s
                    回答中提醒用户以正式政策原文为准。
                    """.formatted(question, objectMapper.writeValueAsString(references));
            return ToolResult.success(llmClient.generateText(prompt));
        } catch (JsonProcessingException ex) {
            return ToolResult.failure("Failed to serialize references", false);
        }
    }
}
