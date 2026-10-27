package com.example.govagent.llm;

import org.springframework.stereotype.Component;

/**
 * 本地可运行的 LLM 模拟实现，不依赖真实模型 API。
 * 真实项目中这里应替换为 ChatGLM 客户端；Agent 其他层不需要知道底层具体模型。
 */
@Component
public class DemoLlmClient implements LlmClient {

    @Override
    public String createPlan(String userMessage, String plannerPrompt) {
        String text = userMessage == null ? "" : userMessage;

        // Demo 为了可离线运行，用关键词模拟“模型规划”；真实实现会由 ChatGLM 根据 Planner Prompt 返回 JSON Plan。
        boolean wantsDocument = containsAny(text, "写", "生成", "起草", "通知", "报告", "公文", "函");
        boolean wantsSearch = containsAny(text, "找", "搜索", "查找", "文档", "材料");
        boolean policyRelated = containsAny(text, "政策", "补贴", "申报", "项目", "企业", "条件");

        if (wantsDocument) {
            return """
                    {
                      "steps": [
                        {
                          "id": "step1",
                          "tool": "policy_search",
                          "arguments": {"query": "%s"},
                          "dependsOn": []
                        },
                        {
                          "id": "step2",
                          "tool": "document_generate",
                          "arguments": {
                            "docType": "通知",
                            "topic": "%s",
                            "references": "${step1.data.documents}"
                          },
                          "dependsOn": ["step1"]
                        }
                      ]
                    }
                    """.formatted(escape(text), escape(text));
        }

        if (wantsSearch && !policyRelated) {
            return """
                    {
                      "steps": [
                        {
                          "id": "step1",
                          "tool": "document_search",
                          "arguments": {"query": "%s"},
                          "dependsOn": []
                        }
                      ]
                    }
                    """.formatted(escape(text));
        }

        return """
                {
                  "steps": [
                    {
                      "id": "step1",
                      "tool": "policy_search",
                      "arguments": {"query": "%s"},
                      "dependsOn": []
                    },
                    {
                      "id": "step2",
                      "tool": "final_answer",
                      "arguments": {
                        "question": "%s",
                        "references": "${step1.data.documents}"
                      },
                      "dependsOn": ["step1"]
                    }
                  ]
                }
                """.formatted(escape(text), escape(text));
    }

    @Override
    public String generateText(String prompt) {
        // Demo 直接返回固定文本；真实实现会把 prompt 发给=】
        // 对应的 ChatGLM 服务。
        if (prompt.contains("公文生成")) {
            return "关于相关事项的通知\n\n根据检索到的政策材料，现将有关事项通知如下：\n"
                    + "一、请相关单位结合实际情况开展材料核验。\n"
                    + "二、符合条件的主体按要求准备申报材料。\n"
                    + "三、办理过程中应以正式政策原文及主管部门最新要求为准。";
        }
        return "根据检索到的政策材料，当前问题可从适用对象、申报条件和办理要求三个方面理解。"
                + "具体办理时应以引用的正式政策原文为准。";
    }

    private static boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
