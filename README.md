# 政务智能问答与公文助手 — Agent Runtime 核心实现

这是一个面向秋招面试复习的 **Spring Boot Agent 核心项目**。重点不是堆业务页面，而是把简历中的三句话真正落到 Java 代码：

1. 统一 Tool 接口 + Spring 自动注册；
2. LLM 只负责生成结构化调用计划，Java 负责校验、执行、重试和降级；
3. Redis/MySQL 会话模型通过接口分层体现，并提供内存实现使项目可直接启动。

> 为了让项目无需 Redis/MySQL/LLM Key 即可运行，示例默认使用 `DemoLlmClient`、`InMemoryHotConversationStore` 和 `InMemoryConversationRepository`。生产环境分别替换成 ChatGLM/OpenAI-compatible API、Redis 和 MySQL 即可，Agent 主链路无需改动。

## 一、核心架构

```text
HTTP Request
    |
AgentController
    |
AgentService
    |---- ConversationService.loadRecent()
    |          |---- HotConversationStore     -> Redis（生产）
    |          `---- ConversationRepository   -> MySQL（生产）
    |
    |---- Planner
    |       `---- LlmPlanner -> LlmClient -> JSON AgentPlan
    |
    `---- AgentExecutor
            |---- ToolRegistry -> Tool
            |---- 参数解析 / 参数校验
            |---- dependsOn 顺序校验
            |---- 重试
            `---- fallback
```

## 二、一次请求的数据流

用户输入：

```text
根据数字化转型政策写一份项目申报通知
```

Planner 产生：

```json
{
  "steps": [
    {
      "id": "step1",
      "tool": "policy_search",
      "arguments": {
        "query": "根据数字化转型政策写一份项目申报通知"
      },
      "dependsOn": []
    },
    {
      "id": "step2",
      "tool": "document_generate",
      "arguments": {
        "docType": "通知",
        "topic": "根据数字化转型政策写一份项目申报通知",
        "references": "${step1.data.documents}"
      },
      "dependsOn": ["step1"]
    }
  ]
}
```

Java 执行过程：

```text
1. 校验 step1 的 tool 是否已注册
2. 校验 query 参数
3. policy_search 执行
4. 将 ToolResult 保存进 AgentContext.stepResults[step1]
5. 校验 step2 dependsOn=[step1] 已完成
6. 将 ${step1.data.documents} 解析为 step1 的真实检索结果
7. 校验 document_generate 参数
8. document_generate 执行
9. 聚合引用 Citation
10. 持久化用户消息、助手消息、执行 Trace
```

## 三、各层职责

### 1. AgentController

只负责 HTTP 边界：接收 `ChatRequest`，做 Bean Validation，然后调用 `AgentService`。

它**不应该**包含 Planner、Tool 选择或会话拼接逻辑，否则控制器会变成不可维护的“大 Controller”。

核心数据：

```java
public record ChatRequest(String userId, String sessionId, String message) {}
public record ChatResponse(String sessionId, String answer,
                           List<Citation> citations,
                           List<ExecutionTrace> trace,
                           boolean fallbackUsed) {}
```

### 2. AgentService

它是一次 Agent 请求的应用层编排入口：

```text
loadRecent
-> new AgentContext
-> planner.plan
-> executor.execute
-> append conversation
-> save execution record
```

它不关心具体有哪些 Tool，也不写 `if (qa) ... else if (document) ...`。

### 3. Planner / LlmPlanner

职责只有一个：**让模型决定“做什么”**。

输入：

```text
用户问题
最近 N 条会话
所有 ToolDefinition
```

输出：

```java
AgentPlan {
    List<PlanStep> steps;
}
```

`LlmPlanner` 对模型结果做两层控制：

- JSON 反序列化；
- 结构校验（step id、tool name、步骤数、重复 id）。

如果模型输出格式错误，会重新要求模型修复；连续失败则生成固定 fallback plan。

### 4. ToolRegistry

Spring 启动时自动注入所有 `Tool` Bean：

```java
public ToolRegistry(List<Tool> toolList)
```

再按 `tool.definition().name()` 建立 Map。

因此新增一个能力只需要：

```java
@Component
public class XxxTool implements Tool { ... }
```

不需要修改 AgentService / AgentExecutor 的核心分发逻辑。

同时 Registry 负责通用参数校验：

```text
必填参数是否缺失
STRING / ARRAY / OBJECT 类型是否匹配
Tool 是否真的存在
```

### 5. AgentExecutor

这是项目最值得面试讲的部分。

Planner 的输出属于“不可信输入”，Executor 才是真正的执行边界。

每一步：

```text
校验 dependsOn
-> Registry 查 Tool
-> 解析 ${stepId.data.xxx}
-> 参数校验
-> Tool.execute
-> 失败是否允许重试
-> ToolResult 写入 AgentContext
```

一个 Tool 连续失败后，整个主 Plan 终止并切换到 fallback Plan。

当前版本只支持**顺序执行**。这样设计的原因是政策检索 -> 公文生成等步骤存在显式数据依赖；后续如果业务需要，可在 `dependsOn` 基础上扩展成 DAG 并行调度。

### 6. Tool

所有能力统一成：

```java
public interface Tool {
    ToolDefinition definition();
    ToolResult execute(AgentContext context, Map<String, Object> arguments);
}
```

项目内提供：

```text
policy_search      政策知识检索（示例模拟 ES）
document_search    内部文档搜索
final_answer       基于检索结果生成最终问答
document_generate  基于政策材料生成公文
```

Tool 返回的不是只有字符串：

```java
ToolResult {
    boolean success;
    String output;
    Map<String, Object> data;
    List<Citation> citations;
    boolean retryable;
    String errorMessage;
}
```

其中 `data` 专门给后续 Tool 使用，`output` 是用户可读结果，`citations` 用于来源追溯。

### 7. AgentContext

一次请求只创建一个，是 Java Agent Runtime 的“工作内存”。

它包含：

```text
userId
sessionId
当前 userMessage
历史 history
stepResults: Map<stepId, ToolResult>
traces: List<ExecutionTrace>
```

例如：

```text
step1 policy_search
    |
    `-- ToolResult.data.documents
              |
              `-- ${step1.data.documents}
                         |
                         v
                step2 document_generate
```

因此多个 Tool 的组合不是靠硬编码方法调用，而是依靠结构化 Plan + AgentContext 做数据传递。

### 8. ConversationService

生产设计：

```text
Redis：最近 N 轮活跃上下文，低延迟读取
MySQL：完整历史消息、Agent 执行记录、审计与追溯
```

本项目中分别抽象为：

```java
HotConversationStore      // Redis 对应接口
ConversationRepository    // MySQL 对应接口
```

默认提供内存实现，因此不用安装数据库也能演示。

读取逻辑是：

```text
先读 Redis-like hot store
命中 -> 返回
未命中 -> 查 durable repository -> 截取最近 N 条 -> 回填 hot store
```

这就是简历中“Redis + MySQL 维护会话、历史上下文”的具体含义。

## 四、为什么模型决策，Java 执行

这套设计中最重要的边界：

```text
LLM / Planner：
决定调用哪个 Tool、顺序、参数

Java / Executor：
决定这个调用是否合法、是否真正执行、失败怎么办
```

因此即使模型输出：

```json
{"tool":"delete_database"}
```

Java 也会因为 `ToolRegistry` 中没有这个 Tool 而拒绝执行。

模型不能直接调用 Controller、Service、SQL 或任意 Java 方法。

## 五、项目目录

```text
src/main/java/com/example/govagent
├── controller
│   ├── AgentController.java
│   └── GlobalExceptionHandler.java
├── service
│   └── AgentService.java
├── planner
│   ├── Planner.java
│   └── LlmPlanner.java
├── llm
│   ├── LlmClient.java
│   └── DemoLlmClient.java
├── executor
│   ├── AgentExecutor.java
│   ├── ArgumentResolver.java
│   └── FallbackPlanFactory.java
├── tool
│   ├── Tool.java
│   ├── ToolRegistry.java
│   └── impl
│       ├── PolicySearchTool.java
│       ├── DocumentSearchTool.java
│       ├── FinalAnswerTool.java
│       └── DocumentGenerateTool.java
├── conversation
│   ├── ConversationService.java
│   ├── HotConversationStore.java
│   ├── ConversationRepository.java
│   ├── InMemoryHotConversationStore.java
│   └── InMemoryConversationRepository.java
└── model
    └── ...
```

## 六、启动

需要 JDK 17+ 和 Maven 3.9+：

```bash
mvn spring-boot:run
```

请求：

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"u1001",
    "message":"根据数字化转型政策写一份项目申报通知"
  }'
```

第二轮把第一次返回的 `sessionId` 带回来即可形成多轮会话：

```json
{
  "userId": "u1001",
  "sessionId": "第一次返回的 sessionId",
  "message": "把刚才的内容再写得正式一点"
}
```

## 七、生产环境如何替换

当前为了可运行使用本地模拟，真正项目中只替换基础设施适配层：

```text
DemoLlmClient
    -> ChatGLM / OpenAI-compatible HTTP Client

InMemoryHotConversationStore
    -> RedisConversationStore

InMemoryConversationRepository
    -> MySQLConversationRepository

PolicySearchTool 内存数据
    -> Elasticsearch query + rerank
```

`AgentController -> AgentService -> Planner -> AgentExecutor -> Tool` 主链路保持不变。

## 八、面试时最值得讲的 4 个类

如果时间有限，优先吃透：

```text
LlmPlanner.java
ToolRegistry.java
AgentExecutor.java
AgentContext.java
```

这四个类正好分别回答：

```text
模型怎么决定？
能力怎么扩展？
Java 怎么安全执行？
多 Tool 怎么传递中间结果？
```
