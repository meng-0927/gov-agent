package com.example.govagent.tool.impl;

import com.example.govagent.model.AgentContext;
import com.example.govagent.model.Citation;
import com.example.govagent.model.ToolDefinition;
import com.example.govagent.model.ToolParamType;
import com.example.govagent.model.ToolParameter;
import com.example.govagent.model.ToolResult;
import com.example.govagent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 政策检索 Tool 的 Demo 实现，用内存数据模拟“召回 + 排序 + 返回 Chunk 元数据”。
 * 当前代码主要展示 Tool 的输入输出结构；真实项目中可替换为向量库/RAG 检索实现。
 */
@Component
public class PolicySearchTool implements Tool {
    private static final List<PolicyDocument> DOCUMENTS = List.of(
            new PolicyDocument(
                    "policy-001", "chunk-01", "关于促进中小企业数字化转型的若干措施",
                    "政策库/数字化转型", "支持符合条件的中小企业开展数字化改造，鼓励建设示范项目，并按申报指南提交材料。"),
            new PolicyDocument(
                    "policy-002", "chunk-03", "企业技术改造项目申报指南",
                    "政策库/技改申报", "申报主体应具备独立法人资格，项目材料一般包括项目说明、投资证明及相关合规材料。"),
            new PolicyDocument(
                    "policy-003", "chunk-02", "政务数据管理办法（示例）",
                    "政策库/数据管理", "政务数据使用应遵循最小必要、授权使用和安全审计原则。")
    );

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "policy_search",
                "从政策知识库检索与用户问题相关的政策片段，并返回可追溯文档元数据。",
                List.of(new ToolParameter("query", ToolParamType.STRING, "检索问题或关键词", true))
        );
    }

    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> arguments) {
        String query = (String) arguments.get("query");

        // 对候选文档做简单相关度打分并取 Top2，用来模拟真实检索系统的召回/排序阶段。
        List<ScoredDocument> ranked = DOCUMENTS.stream()
                .map(doc -> new ScoredDocument(doc, score(query, doc)))
                .sorted(Comparator.comparingInt(ScoredDocument::score).reversed())
                .limit(2)
                .toList();

        // data.documents 供后续 Tool 继续消费；citations 单独用于最终回答的来源追溯。
        List<Map<String, Object>> documents = new ArrayList<>();
        List<Citation> citations = new ArrayList<>();
        StringBuilder readable = new StringBuilder("检索到的政策材料：\n");

        for (ScoredDocument item : ranked) {
            PolicyDocument doc = item.document();
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("documentId", doc.documentId());
            mapped.put("chunkId", doc.chunkId());
            mapped.put("title", doc.title());
            mapped.put("content", doc.content());
            mapped.put("source", doc.source());
            documents.add(mapped);

            citations.add(new Citation(doc.documentId(), doc.chunkId(), doc.title(), doc.source()));
            readable.append("- ").append(doc.title()).append("：").append(doc.content()).append("\n");
        }

        return ToolResult.success(
                readable.toString().trim(),
                Map.of("documents", documents),
                citations
        );
    }

    private int score(String query, PolicyDocument doc) {
        int score = 0;
        String merged = doc.title() + doc.content();
        for (String keyword : List.of("政策", "企业", "数字化", "改造", "申报", "数据", "项目", "补贴")) {
            if (query.contains(keyword) && merged.contains(keyword)) {
                score += 2;
            }
        }
        if (doc.title().contains("数字化") && query.contains("数字化")) {
            score += 4;
        }
        return score;
    }

    private record PolicyDocument(String documentId, String chunkId, String title, String source, String content) {
    }

    private record ScoredDocument(PolicyDocument document, int score) {
    }
}
