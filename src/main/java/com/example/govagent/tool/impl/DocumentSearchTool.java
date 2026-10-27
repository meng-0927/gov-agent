package com.example.govagent.tool.impl;

import com.example.govagent.model.AgentContext;
import com.example.govagent.model.Citation;
import com.example.govagent.model.ToolDefinition;
import com.example.govagent.model.ToolParamType;
import com.example.govagent.model.ToolParameter;
import com.example.govagent.model.ToolResult;
import com.example.govagent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 内部公文搜索能力。Demo 返回固定结果；真实项目中这一层可对接 Elasticsearch，
 * 用于在回答/生成完成后补充高相关内部公文，作为用户可继续查看的参考材料。
 */
@Component
public class DocumentSearchTool implements Tool {
    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "document_search",
                "搜索内部政务材料、会议纪要、办事说明等文档。",
                List.of(new ToolParameter("query", ToolParamType.STRING, "文档搜索关键词", true))
        );
    }

    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> arguments) {
        String query = (String) arguments.get("query");

        // 真实实现：使用 query 查询 ES，根据相关度阈值过滤后返回高命中文档。
        Map<String, Object> doc = Map.of(
                "documentId", "internal-101",
                "title", "数字化转型项目材料清单（示例）",
                "content", "材料包括项目申请表、建设方案、投资情况说明和主体资质材料。",
                "source", "内部文档库/项目材料"
        );
        Citation citation = new Citation(
                "internal-101", "full", "数字化转型项目材料清单（示例）", "内部文档库/项目材料");

        return ToolResult.success(
                "根据关键词“" + query + "”找到：数字化转型项目材料清单（示例）。",
                Map.of("documents", List.of(doc)),
                List.of(citation)
        );
    }
}
