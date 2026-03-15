# Zeno Agent 技术架构文档

## 📋 目录

- [架构概述](#架构概述)
- [系统架构](#系统架构)
- [核心模块](#核心模块)
- [数据流](#数据流)
- [技术选型](#技术选型)
- [部署架构](#部署架构)

## 架构概述

Zeno Agent 是一个面向企业（toB）的服务端 AI Agent 平台，基于 Spring Boot 2.7.18 + LangChain4j 1.11.0 构建，采用前后端分离架构。平台的推理引擎运行在服务端，通过 SSE 向前端推送完整的执行过程，同时支持 PERSONAL MCP 工具由客户端执行的混合模式。

### 设计原则

1. **服务端推理**: Agent 推理循环完全在服务端运行，客户端只负责展示和 PERSONAL 工具执行
2. **模块化 DDD**: 采用领域驱动设计，清晰分层（API / Application / Domain / Infrastructure）
3. **渐进式加载**: Skill 摘要注入 + 全文按需加载；工具名称注入 + Schema 按需解析，突破上下文窗口限制
4. **GLOBAL/PERSONAL 双轨**: 企业统一工具（服务端执行）与用户私有工具（客户端执行）并存
5. **分布式 Human-in-the-loop**: 基于 Redis 阻塞队列实现跨实例的工具确认，支持水平扩展

## 系统架构

### 整体架构图

```
┌──────────────────────────────────────────────────────────────────────┐
│                          前端层（Vue 3）                              │
├──────────────────────────────────────────────────────────────────────┤
│  AgentChat.vue         │  Agent 配置管理    │  知识库管理              │
│  - SSE 流式接收        │  - Agent 定义 CRUD │  - 文档上传              │
│  - PERSONAL 工具执行   │  - Skill 目录树    │  - 向量构建              │
│  - 工具确认（MANUAL）  │  - MCP 绑定配置    │  - 知识库 CRUD           │
└──────────────────────────────────────────────────────────────────────┘
                              │ HTTP / SSE
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        API 层（Spring Boot）                          │
├──────────────────────────────────────────────────────────────────────┤
│  AgentExecutionController   │  AgentDefinitionController              │
│  AgentConversationController│  AgentSkillController                   │
│  McpServerController        │  KnowledgeBaseController               │
│  DocumentController         │  AgentToolController                    │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      应用服务层（Application）                        │
├──────────────────────────────────────────────────────────────────────┤
│  AgentServiceImpl              │  FunctionCallingEngine               │
│  - 创建 SseEmitter             │  - Function Calling 推理循环         │
│  - 异步任务调度                │  - 渐进式工具加载                    │
│  - 预检索 RAG                 │  - GLOBAL / PERSONAL 工具路由        │
│  - 保存消息 / 上下文           │  - MANUAL 模式工具确认               │
│                                │  - Skill 摘要注入                    │
│  AgentContextService          │  AgentSkillService                   │
│  MemorySystem                 │  RAGService                          │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│                       领域层（Domain）                                │
├──────────────────────────────────────────────────────────────────────┤
│  ToolRegistry               │  AgentDefinitionLoader                  │
│  - resolveToolSpecifications│  - 加载 / 缓存 AgentDefinition          │
│  - appendPersonalToolSpecs  │                                         │
│  - isProgressiveMode        │  McpServerService                       │
│  - execute（工具执行路由）  │  - GLOBAL / PERSONAL 服务器管理         │
│                              │                                         │
│  AgentSkill / SkillTreeNode  │  KnowledgeBase / Document              │
│  LoadSkillTool               │  RAGEnhancer                           │
│  ResolveToolsTool            │  EmbeddingProcessor                    │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     基础设施层（Infrastructure）                      │
├──────────────────────────────────────────────────────────────────────┤
│  MCP 客户端层                │  分布式管理                             │
│  McpClientFactory            │  ToolConfirmationManager               │
│  McpTransportFactory         │  - Redisson RBlockingQueue             │
│  McpToolProviderFactory      │  - 跨实例工具确认                      │
│  McpToolExecutor             │  ClientToolCallManager                 │
│                              │  - CompletableFuture 等待回传           │
│  数据访问层                  │  UserAnswerManager                     │
│  MyBatis Mapper              │                                         │
│  Druid DataSource            │  搜索                                   │
│  pgvector EmbeddingStore     │  PlaywrightSearchService               │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│                           数据存储层                                  │
├──────────────────────────────────────────────────────────────────────┤
│  Redis                       │  MySQL                │  PostgreSQL     │
│  - 会话上下文（AgentContext） │  - 会话 / 消息历史    │  - 向量存储     │
│  - 工具确认队列               │  - Agent 定义         │  - pgvector     │
│  - PERSONAL 工具结果等待      │  - MCP 服务器配置     │  - Embedding    │
│  - 短期记忆缓存               │  - Skill 库           │                 │
│  - 分布式锁                  │  - 知识库 / 文档元数据│                 │
└──────────────────────────────────────────────────────────────────────┘
```

## 核心模块

### 1. Agent 定义与配置（AgentDefinition）

`AgentDefinition` 是平台的核心配置实体，包含四大维度：

```
AgentDefinition
├── systemPrompt           - 系统提示词
├── ToolsConfig
│   ├── serverMcpIds       - 绑定的 GLOBAL MCP 服务器 ID 列表
│   ├── personalMcpCapabilities  - 绑定的 PERSONAL MCP 能力标签
│   ├── systemTools        - 启用的系统内置工具（system_load_skill 等）
│   └── knowledgeIds       - 绑定的知识库 ID 列表
├── ContextConfig
│   ├── historyMessageLoadLimit  - 历史消息加载上限（默认 20）
│   └── maxToolRounds      - 最大工具调用轮数（默认 8）
├── RAGConfig              - RAG 检索参数（阈值 / Top-K 等）
└── skillTree              - Agent 私有 Skill 目录树（SkillTreeNode 列表）
```

**运行时配置优先级**（高 → 低）：
1. 前端 `AgentRequest` 中的运行时参数（mode、modelId 等）
2. `AgentDefinition` 中持久化的默认配置
3. `AgentRuntimeConfig` 字段默认值

### 2. FunctionCallingEngine（推理引擎）

基于 Function Calling 的推理循环，是 Agent 执行的核心：

```
初始化
  ↓
加载 AgentDefinition → 构建 System Prompt（含 Skill 摘要段落）
  ↓
解析工具列表（isProgressiveMode?）
  ├── 普通模式: 全部 ToolSpecification 立即注入
  └── 渐进式模式: 只注入工具名+描述，加入 system_resolve_tools
  ↓
追加 PERSONAL MCP 工具（来自前端 prefetch schema）
  ↓
推理循环（最多 maxToolRounds 轮）
  ├── LLM 流式生成
  │   ├── FinishReason = STOP → 结束循环
  │   └── FinishReason = TOOL_EXECUTION → 执行工具
  │       ├── 判断 manualMode
  │       │   └── 是: SSE 推送确认事件 → 阻塞等待 Redis 队列 → APPROVED/REJECTED
  │       ├── 判断 personalTool
  │       │   └── 是: SSE 下发 PERSONAL_TOOL_CALL → 阻塞等待 CompletableFuture
  │       └── 否: ToolRegistry.execute() 服务端直接执行
  │
  └── 渐进式模式: 检测 activeMcpToolNames → 追加对应 ToolSpecification
  ↓
返回 AgentExecutionResult
```

### 3. GLOBAL / PERSONAL MCP 双模式架构

#### 3.1 GLOBAL MCP

```
McpServerEntity (scope=0)
  ↓
McpClientFactory.getOrCreateClient()      - 懒加载 + 复用客户端
  ↓
McpTransportFactory.createTransport()
  ├── streamable-http → StreamableHttpMcpTransport（远端推荐）
  ├── stdio           → StdioMcpTransport（本地进程）
  ├── websocket       → WebSocketMcpTransport
  └── sse             → SseMcpTransport
  ↓
McpToolProviderFactory.createFilteredToolProvider(serverIds)
  ↓
ToolRegistry.resolveToolSpecifications()  - 按 Agent 绑定过滤工具
  ↓
FunctionCallingEngine → LLM 调用工具 → McpToolExecutor.execute()
```

#### 3.2 PERSONAL MCP

```
McpServerEntity (scope=1)
  - authHeader 字段只存 Header 名（值为空），运行时由浏览器补充
  ↓
前端发送消息前
  └── prefetch: 调用 MCP tools/list → 获取 PersonalMcpToolSchema
  └── 将 schema 列表随 AgentRequest 上传
  ↓
后端 ToolRegistry.appendPersonalToolSpecs()
  └── 解析 inputSchema → 构造 ToolSpecification → 追加到工具列表
  ↓
FunctionCallingEngine 检测到 PERSONAL 工具被 LLM 调用
  ↓
AgentEventPublisher.onPersonalToolCall(callId, toolName, serverId, arguments)
  └── SSE 推送 PERSONAL_TOOL_CALL 事件给浏览器
  ↓
浏览器根据 serverId 路由到对应 MCP 服务器执行工具
  └── POST /api/mcp/client-tool-result { callId, result }
  ↓
ClientToolCallManager.complete(callId, result)
  └── CompletableFuture.complete() → 唤醒后端推理线程
  ↓
推理继续，工具结果追加到对话历史
```

### 4. Skill 机制

#### 4.1 数据模型

```
AgentSkill
├── id        - 全局唯一 Skill ID
├── name      - 技能名称
├── summary   - 摘要（一行，注入 System Prompt）
├── content   - 全文（按需加载，可达数千字）
└── tags      - 标签列表，用于配置页筛选

SkillTreeNode（目录树节点）
├── id        - 节点 ID（在 Agent 内唯一）
├── label     - 显示名称
├── enabled   - 是否启用（false 则不注入）
├── skillId   - 引用的 AgentSkill ID（叶节点专用）
└── children  - 子节点列表（目录节点使用）
```

#### 4.2 渐进式注入流程

```
System Prompt 构建阶段
  遍历 Agent.skillTree（DFS）→ 收集所有 enabled=true 的叶节点
  批量查询 AgentSkill（避免 N+1）
  注入：
    ## 可用技能列表
    - [skill-001] 数据库查询规范: 查询时必须使用参数化 SQL...
    - [skill-002] 错误处理模板: 遇到业务异常时按以下格式输出...

LLM 推理阶段（按需加载）
  LLM 判断需要技能 → 调用 system_load_skill("skill-001")
  LoadSkillTool.execute() → 从数据库查询 content 字段
  返回："# 数据库查询规范\n\n[完整内容...]"
  LLM 获得完整规范后执行对应任务
```

### 5. 工具渐进式加载（Progressive Tool Loading）

#### 5.1 触发条件

```java
// ToolRegistry.isProgressiveMode()
boolean hasResolveTools = agentDef.getTools().getSystemTools()
    .stream().anyMatch("system_resolve_tools"::equalsIgnoreCase);
int mcpToolCount = mcpManager.getToolsByServerIds(serverMcpIds).size();
return hasResolveTools && mcpToolCount > progressiveThreshold;
```

#### 5.2 工作流程

```
普通模式（工具数 ≤ 阈值）
  初始 toolSpecs = 全部 ToolSpecification（含完整参数 Schema）

渐进式模式（工具数 > 阈值）
  初始 toolSpecs = [system_resolve_tools]
  System Prompt 追加：
    ## 可用 MCP 工具（按需加载）
    - list_files: 列出指定目录下的文件
    - read_file: 读取文件内容
    - ...

  LLM 调用 system_resolve_tools(["list_files"])
    → context.activeMcpToolNames.add("list_files")
    → 工具执行完成后检测到 activeMcpToolNames 非空
    → 从 McpManager 查询 "list_files" 的完整 ToolSpecification
    → toolSpecs.addAll(newSpecs) → activeMcpToolNames.clear()
    → 下一轮 LLM 可直接调用 list_files
```

### 6. MANUAL 模式 / Human-in-the-loop

#### 6.1 工具确认流程

```
FunctionCallingEngine（MANUAL 模式下每次工具调用）
  ↓
ToolConfirmationManager.register(toolExecutionId)
  └── Redis: SETEX aiagent:tool:confirm:{id} 5min
  ↓
AgentEventPublisher.onToolCall(toolName, args, needConfirm=true, toolExecutionId)
  └── SSE: { type: "tool_call", toolExecutionId, needConfirm: true, ... }
  ↓
toolConfirmationManager.waitForDecision(toolExecutionId, timeoutMs)
  └── Redisson RBlockingQueue.poll(timeout) —— 阻塞等待
  ↓
前端用户点击"批准" / "拒绝"
  └── POST /aiagent/mcp/tool-confirm { toolExecutionId, action: "APPROVE" }
  ↓
ToolConfirmationManager.approve(toolExecutionId)
  └── Redisson RBlockingQueue.offer("APPROVED")
  ↓
waitForDecision() 返回 APPROVED → 执行工具
或返回 REJECTED → 构造拒绝消息 → LLM 重新规划
或 poll 超时 → 返回 TIMEOUT → LLM 感知超时重新规划
```

#### 6.2 分布式特性

基于 `Redisson RBlockingQueue` 实现：多实例部署下，处理前端回调的节点与执行推理的节点可以不同，Redis 消息队列保证跨节点通信。

### 7. RAG 知识检索模块

#### 7.1 文档处理流程

```
文档上传 → DocumentParser（Apache Tika）解析
  ↓
文本分段（默认 1000 字符，50 字符重叠）
  ↓
EmbeddingProcessor → Embedding 模型向量化
  ↓
pgvector EmbeddingStore（PostgreSQL）存储
```

#### 7.2 检索与增强流程

```
Agent 执行前预检索（performInitialRagRetrieval）
  └── 将结果存入 context.initialRagResult
  ↓
RAGService.retrieve(query, knowledgeIds)
  └── 向量化查询 → pgvector 余弦相似度搜索 → 过滤低分结果
  ↓
检索结果注入 System Prompt / 用户消息上下文
  ↓
LLM 基于检索内容生成回答
```

### 8. 记忆管理模块

```
短期记忆（Redis）
  - 存储 AgentContext（含消息历史、工具调用历史、RAG 检索历史）
  - Key: aiagent:context:{conversationId}
  - TTL: 可配置（默认 24 小时）

长期记忆（MySQL）
  - 持久化所有会话和消息（agent_conversation / agent_message）
  - 会话重新激活时从 MySQL 加载历史消息（按 historyMessageLoadLimit 限制）

记忆加载优先级
  Redis 命中 → 直接使用缓存上下文
  Redis 未命中 → 从 MySQL 重建 AgentContext
```

## 数据流

### Agent 执行完整数据流

```
前端发送请求（含 PersonalMcpToolSchema 列表）
  ↓
AgentExecutionController → AgentServiceImpl.execute()
  ↓
创建 SseEmitter → CompletableFuture.runAsync() 异步执行
  ↓
AgentContextService.loadOrCreateContext()
  ├── Redis 命中 → 加载缓存上下文
  └── 未命中 → 从 MySQL 加载历史消息 → 新建 AgentContext
  ↓
performInitialRagRetrieval()（如 Agent 绑定了知识库）
  ↓
FunctionCallingEngine.execute(context)
  ├── 构建 System Prompt（含 Skill 摘要）
  ├── 解析工具列表（普通 / 渐进式）
  ├── 追加 PERSONAL MCP 工具
  └── 推理循环（每步通过 StreamingCallback → SSE 推送）
  ↓
saveNewAssistantMessages()
  ↓
memorySystem.saveContext()（Redis + MySQL）
  ↓
streamingService.closeEmitter()
```

### SSE 事件类型

| 事件类型 | 说明 | 关键数据 |
|---------|------|---------|
| `agent:start` | 任务开始 | requestId, conversationId |
| `agent:thinking` | 推理中 | statusText |
| `agent:rag_retrieve` | RAG 检索中 | query, documentCount |
| `agent:tool_call` | 工具调用（GLOBAL）| toolName, args, needConfirm, toolExecutionId |
| `agent:personal_tool_call` | PERSONAL 工具下发 | callId, toolName, serverId, arguments |
| `agent:tool_result` | 工具执行结果 | result, success, durationMs |
| `agent:message` | 流式 Token | content（增量） |
| `agent:complete` | 任务完成 | finalMessage, tokens, durationMs, toolRounds |
| `agent:error` | 错误发生 | error, message |

## 技术选型

### 后端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 2.7.18 | 应用框架 |
| LangChain4j | 1.11.0 | LLM 抽象层 / MCP 客户端 / pgvector 集成 |
| MyBatis | 2.3.1 | ORM 框架 |
| Druid | - | 数据库连接池 |
| Redis | 6.0+ | 上下文缓存 / 分布式队列 |
| Redisson | 3.23.5 | 分布式锁 / RBlockingQueue |
| MySQL | 8.0+ | 关系数据库 |
| PostgreSQL + pgvector | 14+ | 向量数据库 |
| Apache Tika | 2.9.1 | 文档格式解析 |
| Playwright | 1.44.0 | 无头浏览器搜索 |
| Jsoup | 1.18.3 | HTML 解析 |

### 前端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.3+ | 前端框架 |
| TypeScript | 5.0+ | 类型系统 |
| Vite | 5.0+ | 构建工具 |
| Ant Design Vue | 4.0+ | UI 组件库 |
| Axios | 1.6+ | HTTP 客户端 |
| Markdown-it | 14.0+ | Markdown 渲染 |

## 部署架构

### 开发环境

```
┌─────────────┐
│  前端 (Vite) │  :5173
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 后端 (Spring)│  :8080
└──────┬──────┘
       │
       ├──► Redis     :6379
       ├──► MySQL     :3306
       └──► PostgreSQL :5432
```

### 生产环境（Docker）

```
┌─────────────────┐
│  Nginx          │  :80/443
│  - 前端静态文件  │
│  - API 反向代理  │
│  - SSE 流式配置  │  proxy_buffering off; proxy_read_timeout 300s;
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Spring Boot    │  :8080
│  - 应用服务      │
└────────┬────────┘
         │
         ├──► Redis     :6379  （上下文缓存 / 工具确认队列）
         ├──► MySQL     :3306  （持久化）
         └──► PostgreSQL :5432  （向量存储）
```

### 水平扩展

系统支持无状态水平扩展：

- **AgentContext** 存储于 Redis，多实例共享
- **工具确认队列**（`ToolConfirmationManager`）基于 Redisson RBlockingQueue，跨节点可见
- **PERSONAL 工具回传**（`ClientToolCallManager`）使用 CompletableFuture，需路由到同一节点（建议按 conversationId 做会话粘连）

## 性能优化

### 1. 渐进式加载

- Skill 摘要注入：System Prompt 保持轻量，不因 Skill 数量增加而膨胀
- 工具渐进式加载：超大规模工具集不受 LLM Token 限制

### 2. 缓存策略

- Redis 缓存 AgentContext：避免每次从 MySQL 重建对话历史
- AgentDefinition 本地缓存：减少频繁查询数据库

### 3. 异步处理

- SSE 流式响应：Agent 推理在独立线程，LLM 生成内容增量推送，用户无需等待完整响应
- 批量查询 Skill：构建 System Prompt 时一次性批量查询所有 Skill，避免 N+1

### 4. 数据库连接池

- Druid 连接池管理 MySQL 连接
- pgvector 连接复用

## 安全考虑

### 1. PERSONAL MCP 密钥隔离

PERSONAL MCP 服务器的认证密钥（API Key 等）只存储在用户浏览器 localStorage，服务端数据库只存储 Header 名（值为空）。即使数据库泄露，也不会暴露用户的私有 API Key。

### 2. GLOBAL MCP 密钥加密

GLOBAL MCP 服务器的认证信息（`authHeader`、`extraHeaders`）在数据库中加密存储，通过 AES 等对称加密保护。

### 3. MANUAL 模式风控

对于可能影响生产数据的危险工具，可将 Agent 配置为 MANUAL 模式，要求人工确认后才执行，提供企业级风控保障。

### 4. API 安全

- Spring Validation 参数校验
- 全局异常处理器统一处理错误
- CORS 跨域访问策略配置

### 5. 敏感信息

- API Key 等敏感信息通过环境变量注入，不硬编码在代码中
- MyBatis 参数化查询防止 SQL 注入

## 监控和日志

### 日志分级

- `application.log` - 应用主日志
- `api.log` - API 访问日志
- `error.log` - 错误单独记录
- Logback 滚动策略，按天分割

### 健康检查

```bash
curl http://localhost:8080/aiagent/health
```

### 可观测指标

- 每次推理记录 Token 用量、工具调用轮数、总耗时（随 `agent:complete` 事件推送）
- SSE 事件链路完整记录推理过程（可接 ELK / OpenTelemetry）

## 总结

Zeno Agent 采用服务端推理 + GLOBAL/PERSONAL MCP 双模式 + 渐进式加载的创新架构，解决了企业 AI Agent 落地中的三大核心问题：

1. **大规模工具集管理**: 渐进式工具加载突破 LLM 上下文限制，支持绑定数十乃至上百个工具
2. **用户私有工具安全接入**: PERSONAL MCP 使密钥永不离开用户侧，满足企业数据合规要求
3. **关键操作风险管控**: MANUAL 模式 + 分布式 Human-in-the-loop 提供企业级审批流程
