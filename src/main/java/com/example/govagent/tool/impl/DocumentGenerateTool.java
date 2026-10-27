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
 * 公文生成能力：接收公文类型、主题和前序步骤提供的参考材料，再调用大模型生成草稿。
 * 在真实项目中可以对应专门的 ChatGLM 公文生成模型。
 */
@Component
public class DocumentGenerateTool implements Tool {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public DocumentGenerateTool(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "document_generate",
                "根据主题和前序检索材料生成通知、函、报告等政务公文草稿。",
                List.of(
                        new ToolParameter("docType", ToolParamType.STRING, "公文类型", true),
                        new ToolParameter("topic", ToolParamType.STRING, "公文主题", true),
                        new ToolParameter("references", ToolParamType.ARRAY, "作为事实依据的政策材料", true)
                )
        );
    }

    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> arguments) {
        try {
            // references 往往来自前序检索 Tool，由 ArgumentResolver 在执行前解析成真实 List。
            String prompt = """
                    【公文生成】
                    公文类型：%s
                    主题：%s
                    政策参考：%s
                    要求：结构规范、表述克制，不得创造参考材料中不存在的政策事实。
                    """.formatted(
                    arguments.get("docType"),
                    arguments.get("topic"),
                    objectMapper.writeValueAsString(arguments.get("references")));
            return ToolResult.success(llmClient.generateText(prompt));
        } catch (JsonProcessingException ex) {
            return ToolResult.failure("Failed to serialize document references", false);
        }
    }
}
