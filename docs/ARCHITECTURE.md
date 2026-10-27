# Agent 架构面试讲解稿

## 1. 一句话定义

这个项目不是“模型直接调用后端接口”，而是一个 **Planner + Java Executor** 的 Agent Runtime：模型输出结构化计划，Java 对计划做白名单、依赖、参数和异常控制，然后调用标准化 Tool。

## 2. 核心调用链

```text
POST /api/agent/chat
        |
        v
AgentController
        |
        v
AgentService
        |
        +--> ConversationService.loadRecent(sessionId)
        |
        +--> AgentContext
        |
        +--> Planner.plan(context, toolDefinitions)
        |       |
        |       `--> LlmClient.createPlan(...) -> JSON
        |
        +--> AgentExecutor.execute(plan, context)
        |       |
        |       +--> dependency validation
        |       +--> ToolRegistry.getRequired(tool)
        |       +--> ArgumentResolver
        |       +--> parameter validation
        |       +--> Tool.execute
        |       +--> retry
        |       `--> fallback
        |
        `--> ConversationService.append/saveRun
```

## 3. Planner 为什么不能直接执行

LLM 输出具有概率性。Tool 名称可能幻觉，参数可能缺失，JSON 可能坏，甚至可能生成超出授权范围的动作。

因此模型只拥有“建议调用”的权力，没有“真实执行”的权力。

真实执行权限固定在 Java：

```text
LLM JSON
  |
  v
ToolRegistry 白名单
  |
参数 schema
  |
dependsOn
  |
Tool.execute
```

## 4. 为什么不是 if/else

固定路由：

```java
if (type == QA) search();
else if (type == DOCUMENT) generate();
```

每加一个能力都要修改核心分发逻辑，而且只能描述固定链路。

当前设计：

```text
Tool Bean -> ToolRegistry
               |
Planner <------+
   |
AgentPlan
   |
Executor
```

新增能力只需要实现 Tool 并注册为 Spring Bean；已有 Executor 不需要知道业务类型。

## 5. Tool 间数据怎么传

Plan 中显式声明：

```json
{
  "id":"step2",
  "tool":"document_generate",
  "dependsOn":["step1"],
  "arguments":{
    "references":"${step1.data.documents}"
  }
}
```

`step1` 执行结束后：

```java
context.putStepResult("step1", result);
```

执行 step2 前：

```java
argumentResolver.resolve(step.arguments(), context);
```

解析器将占位符替换为真正的 List<Map<...>>。

## 6. ToolResult 为什么拆 output/data/citations

这是容易被忽略但很适合面试讲的设计点。

```text
output      用户可读的文本

data        机器可消费的结构化中间结果

citations   来源追溯元数据
```

如果只有一个 String，后续 Tool 要么重新解析自然语言，要么强耦合前一个 Tool 的格式。

拆开后，Agent 组合链路更加稳定。

## 7. 失败处理

### Planner 输出坏 JSON

```text
parse fail
-> 把上一次原始输出 + 错误信息追加进修复提示
-> 再调用模型
-> 连续失败 -> 固定 fallback plan
```

### Tool 调用失败

```text
ToolResult.failure(retryable=true)
-> Executor 重试
-> 达到最大次数
-> 主 Plan 失败
-> fallback Plan
```

### Tool 名称幻觉

```text
ToolRegistry.getRequired(name)
-> 不存在
-> 拒绝执行
-> fallback
```

## 8. Redis + MySQL

生产形态建议：

```text
Redis key: agent:session:{sessionId}
value: 最近 N 条消息 / 会话摘要
TTL: 按业务设置

MySQL conversation_message
- id
- session_id
- role
- content
- created_at

MySQL agent_run
- id
- session_id
- request
- final_answer
- trace_json
- created_at
```

Redis 负责快速热上下文，MySQL 负责完整历史和审计。

## 9. 为什么现在串行，不并行

当前重点链路往往存在依赖：

```text
policy_search
    |
    v
document_generate
```

第二步必须消费第一步的结果。因此第一版先保证可解释、可重试、可追踪的顺序执行。

如果以后出现：

```text
policy_search       document_search
       \             /
        \           /
          final_answer
```

可以直接利用 `dependsOn` 扩展为 DAG：依赖满足的步骤放进线程池并行执行。

## 10. 最容易被追问的实现细节

- Tool 如何避免重名：Registry 启动时检查 duplicate name，直接 fail-fast。
- 模型调用不存在 Tool：Registry 白名单拒绝。
- 参数非法：ToolDefinition + ToolParameter 做通用校验。
- 前一步失败：依赖步骤不会执行。
- 是否无限重试：不会，Executor 配置最大次数。
- 引用怎么做：搜索 Tool 返回 documentId/chunkId/title/source，Executor 聚合 Citation。
- 多轮历史怎么控制长度：ConversationService 只取最近 N 条；生产环境可增加摘要。
- 为什么 ToolResult 不是 Object：统一协议，避免 Executor 了解具体业务返回类型。
