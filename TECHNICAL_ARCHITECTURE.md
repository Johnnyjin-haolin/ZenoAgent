# Zeno Agent 技术架构文档

## 📋 目录

- [架构概述](#架构概述)
- [系统架构](#系统架构)
- [核心模块](#核心模块)
- [数据流](#数据流)
- [技术选型](#技术选型)
- [部署架构](#部署架构)

## 架构概述

Zeno Agent 是一个基于 Spring Boot 和 Vue 3 的 AI Agent 平台，采用前后端分离架构，集成了 LangChain4j 框架，提供智能对话、RAG 知识检索、MCP 工具调用等核心能力。

### 设计原则

1. **模块化设计**: 采用类领域驱动设计（DDD），清晰的分层架构
2. **可扩展性**: 支持多 LLM 提供商、多工具类型、多知识库
3. **高性能**: Redis 缓存、流式响应、异步处理
4. **易维护**: 清晰的代码结构、完善的文档、统一的错误处理

## 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        前端层 (Vue 3)                        │
├─────────────────────────────────────────────────────────────┤
│  AgentChat.vue  │  知识库管理  │  会话管理  │  配置管理      │
│  - SSE 流式接收  │  - 文档上传  │  - 会话列表 │  - 模型选择   │
│  - 状态展示      │  - 知识库CRUD│  - 消息历史 │  - 工具配置   │
└─────────────────────────────────────────────────────────────┘
                            │ HTTP/SSE
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    API 层 (Spring Boot)                      │
├─────────────────────────────────────────────────────────────┤
│  AgentExecutionController  │  KnowledgeBaseController        │
│  AgentConversationController│  DocumentController            │
│  AgentMetadataController    │  AgentToolController            │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   应用服务层 (Application)                   │
├─────────────────────────────────────────────────────────────┤
│  AgentService      │  RAGEnhancer      │  MemorySystem      │
│  - 任务执行        │  - 向量检索        │  - 上下文管理      │
│  - 流式响应        │  - 知识增强        │  - 记忆存储        │
│                    │                    │                    │
│  ReActEngine       │  ThinkingEngine    │  ActionExecutor    │
│  - 推理循环        │  - 任务分析        │  - 动作执行        │
│  - 迭代控制        │  - 规划生成        │  - 结果处理        │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   基础设施层 (Infrastructure)                 │
├─────────────────────────────────────────────────────────────┤
│  LLM 集成          │  MCP 工具集成      │  数据访问层        │
│  - LangChain4j     │  - MCP 客户端      │  - MyBatis        │
│  - OpenAI         │  - 工具发现        │  - Redis            │
│  - DeepSeek        │  - 工具调用        │  - PostgreSQL     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                        数据存储层                            │
├─────────────────────────────────────────────────────────────┤
│  Redis              │  MySQL            │  PostgreSQL       │
│  - 会话上下文       │  - 会话持久化      │  - 向量存储        │
│  - 短期记忆         │  - 消息历史        │  - Embedding      │
│  - 缓存             │  - 知识库元数据    │  - 文档向量       │
└─────────────────────────────────────────────────────────────┘
```

## 核心模块

### 1. Agent 执行模块

#### 1.1 ReAct 引擎

ReAct（Reasoning + Acting）是项目的核心推理引擎，实现了思考-行动-观察的循环模式。

**核心类**:
- `ReActEngine`: 主引擎，控制迭代循环
- `ThinkingEngine`: 思考引擎，生成动作和计划
- `ObservationEngine`: 观察引擎，评估执行结果
- `ActionExecutor`: 动作执行器，执行具体动作

**执行流程**:

```
1. 接收用户请求
   ↓
2. ThinkingEngine 分析任务
   - 任务分类（简单对话/知识查询/工具调用/复杂工作流）
   - 生成思考过程
   - 生成行动计划
   ↓
3. ActionExecutor 执行动作
   - RAG 检索（如需要）
   - 工具调用（如需要）
   - LLM 生成（如需要）
   ↓
4. ObservationEngine 观察结果
   - 评估执行结果
   - 判断是否需要继续迭代
   ↓
5. 如果继续，返回步骤 2
   如果完成，返回最终结果
```

#### 1.2 任务分类

系统自动将用户请求分类为以下类型：

- **SIMPLE_CHAT**: 简单对话，直接使用 LLM 生成回复
- **RAG_QUERY**: 知识查询，需要从知识库检索相关信息
- **TOOL_CALL**: 工具调用，需要调用 MCP 工具
- **COMPLEX_WORKFLOW**: 复杂工作流，需要多步骤执行

#### 1.3 模型选择

根据任务类型自动选择最优模型：

```yaml
task-model-mapping:
  SIMPLE_CHAT:
    - gpt-4o-mini      # 优先使用
  RAG_QUERY:
    - gpt-4o-mini
  TOOL_CALL:
    - gpt-4o           # 优先使用，失败则降级
    - gpt-4o-mini
  COMPLEX_WORKFLOW:
    - gpt-4o
    - gpt-4o-mini
```

### 2. RAG 知识检索模块

#### 2.1 文档处理流程

```
文档上传
   ↓
DocumentParser 解析文档
   - PDF/Word/TXT/Markdown 等格式
   ↓
文档分段
   - 按固定大小分段（默认 1000 字符）
   - 支持重叠（默认 50 字符）
   ↓
EmbeddingProcessor 向量化
   - 使用 Embedding 模型生成向量
   ↓
存储到 PostgreSQL
   - 使用 pgvector 扩展存储向量
```

#### 2.2 检索流程

```
用户查询
   ↓
RAGEnhancer 处理
   ↓
1. 使用 Embedding 模型将查询向量化
   ↓
2. 在 PostgreSQL 中执行相似度搜索
   - 使用余弦相似度
   - 过滤低分结果（默认阈值 0.5）
   ↓
3. 检索相关文档片段
   ↓
4. 将检索结果注入到 LLM 提示词
   ↓
5. LLM 基于检索内容生成回答
```

#### 2.3 知识库管理

- **知识库创建**: 支持创建多个知识库，独立管理文档
- **文档管理**: 支持文档上传、删除、重建向量
- **统计信息**: 提供知识库文档数量、向量数量等统计

### 3. MCP 工具调用模块

#### 3.1 MCP 协议

MCP (Model Context Protocol) 是一个标准化的工具调用协议。

**核心组件**:
- `McpClientManager`: MCP 客户端管理器
- `McpToolRegistry`: 工具注册表
- `McpGroupManager`: 工具分组管理
- `McpToolInvoker`: 工具调用器

#### 3.2 工具发现

```
1. 读取 MCP 配置文件（mcp.json）
   ↓
2. 连接到 MCP 服务器
   ↓
3. 调用 list_tools 获取工具列表
   ↓
4. 注册工具到 ToolRegistry
   ↓
5. 按分组组织工具
```

#### 3.3 工具调用流程

```
用户请求需要工具调用
   ↓
IntelligentToolSelector 选择工具
   - 根据工具描述和用户需求匹配
   ↓
ActionExecutor 执行工具调用
   ↓
如果是手动模式，等待用户确认
   ↓
McpToolInvoker 调用工具
   ↓
解析工具执行结果
   ↓
将结果传递给 LLM 生成最终回答
```

### 4. 记忆管理模块

#### 4.1 记忆类型

- **短期记忆**: 存储在 Redis，用于当前会话的上下文
- **长期记忆**: 存储在 MySQL，持久化会话和消息历史

#### 4.2 上下文管理

```
用户发送消息
   ↓
MemorySystem 获取上下文
   - 从 Redis 获取短期记忆（最近 N 轮对话）
   - 从 MySQL 获取历史消息（如需要）
   ↓
构建上下文提示词
   ↓
传递给 LLM
   ↓
保存新的消息到 Redis 和 MySQL
```

## 数据流

### Agent 执行数据流

```
前端发送请求
   ↓
AgentExecutionController 接收
   ↓
AgentService.execute()
   ↓
创建 SseEmitter（SSE 流式响应）
   ↓
异步执行任务
   ↓
ReActEngine 执行推理循环
   ↓
每个步骤通过 StreamingCallback 发送事件
   ↓
前端通过 SSE 接收事件并实时展示
```

### SSE 事件类型

| 事件类型 | 说明 | 数据内容 |
|---------|------|---------|
| `agent:start` | 任务开始 | requestId, conversationId |
| `agent:thinking` | AI 思考中 | message, statusText |
| `agent:model_selected` | 模型已选择 | modelId, modelName |
| `agent:rag_retrieve` | RAG 检索中 | query, documentCount |
| `agent:tool_call` | 工具调用中 | toolName, params |
| `agent:tool_result` | 工具执行结果 | result, success |
| `agent:message` | 流式内容 | content (增量) |
| `agent:complete` | 任务完成 | finalMessage, tokens, duration |
| `agent:error` | 错误发生 | error, message |

## 技术选型

### 后端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 2.7.18 | 应用框架 |
| LangChain4j | 1.9.1 | LLM 抽象层 |
| MyBatis | 2.3.1 | ORM 框架 |
| Redis | 6.0+ | 缓存和会话存储 |
| MySQL | 8.0+ | 关系数据库 |
| PostgreSQL | 14+ | 向量数据库 |
| Redisson | 3.23.5 | 分布式锁 |

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
       ├──► Redis :6379
       ├──► MySQL :3306
       └──► PostgreSQL :5432
```

### 生产环境（Docker）

```
┌─────────────────┐
│  Nginx          │  :80/443
│  - 前端静态文件  │
│  - API 反向代理  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Spring Boot    │  :8080
│  - 应用服务      │
└────────┬────────┘
         │
         ├──► Redis :6379
         ├──► MySQL :3306
         └──► PostgreSQL :5432
```

### 容器编排

使用 Docker Compose 编排所有服务：

- `backend`: Spring Boot 应用
- `frontend`: Nginx 服务前端静态文件
- `redis`: Redis 缓存
- `mysql`: MySQL 数据库（可选，如使用外部数据库）
- `postgres`: PostgreSQL 数据库（可选，如使用外部数据库）

## 性能优化

### 1. 缓存策略

- **Redis 缓存**: 会话上下文、模型列表、工具列表等
- **本地缓存**: 配置信息、枚举值等

### 2. 异步处理

- **SSE 流式响应**: 实时推送执行过程，提升用户体验
- **异步任务执行**: Agent 任务在独立线程中执行

### 3. 数据库优化

- **连接池**: 使用 Druid 连接池管理数据库连接
- **索引优化**: 为常用查询字段添加索引
- **分页查询**: 列表查询使用分页，避免一次性加载大量数据

## 安全考虑

### 1. API 安全

- **参数校验**: 使用 Spring Validation 进行参数校验
- **统一异常处理**: 全局异常处理器统一处理错误
- **CORS 配置**: 配置跨域访问策略

### 2. 数据安全

- **敏感信息**: API Key 等敏感信息通过环境变量配置
- **SQL 注入防护**: 使用 MyBatis 参数化查询
- **XSS 防护**: 前端对用户输入进行转义

### 3. 工具调用安全

- **手动确认模式**: 危险操作支持手动确认
- **工具权限控制**: 支持按工具分组控制权限

## 扩展性设计

### 1. 多 LLM 提供商支持

通过 LangChain4j 的抽象层，可以轻松添加新的 LLM 提供商：

```yaml
aiagent:
  llm:
    models:
      - id: gpt-4o
        provider: OPENAI
      - id: deepseek-chat
        provider: DEEPSEEK
      - id: claude-3
        provider: ANTHROPIC  # 未来支持
```

### 2. 插件系统

未来可以支持插件系统，允许第三方扩展功能：

- 自定义工具
- 自定义知识库处理
- 自定义推理引擎

### 3. 分布式部署

支持水平扩展：

- 无状态服务设计，可以部署多个实例
- Redis 作为共享缓存和会话存储
- 数据库支持主从复制

## 监控和日志

### 1. 日志

- **应用日志**: 使用 Logback，输出到文件和控制台
- **访问日志**: 记录 API 访问日志
- **错误日志**: 单独记录错误日志

### 2. 监控指标

- **健康检查**: `/aiagent/health` 端点
- **性能指标**: 记录请求耗时、Token 使用量等
- **业务指标**: 会话数量、消息数量、工具调用次数等

## 总结

Zeno Agent 采用现代化的技术栈和清晰的分层架构，实现了完整的 AI Agent 功能。通过 ReAct 推理引擎、RAG 知识检索、MCP 工具调用等核心模块，为用户提供了强大的 AI 助手能力。项目具有良好的扩展性和可维护性，适合作为 AI Agent 平台的基础框架。
