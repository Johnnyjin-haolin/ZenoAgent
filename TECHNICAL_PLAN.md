# AI Agent 轻量化独立项目技术方案

## 项目概述

将 AI Agent 功能从 JeecG Boot 平台中剥离，创建一个轻量化的前后端独立项目，**不使用 MySQL 数据库**，保留 RAG、MCP、Agent Chat 三大核心能力。

## 技术架构

### 后端架构

```
┌─────────────────────────────────────────┐
│      Spring Boot 2.7.18 应用           │
├─────────────────────────────────────────┤
│  Controller Layer                       │
│  - AgentController (REST API)          │
│  - SSE 流式响应                         │
├─────────────────────────────────────────┤
│  Service Layer                          │
│  - TaskClassifier (任务分类)            │
│  - RAGEnhancer (知识检索)               │
│  - ToolOrchestrator (MCP工具编排)       │
│  - MemorySystem (记忆管理)              │
├─────────────────────────────────────────┤
│  Storage Layer                          │
│  - Redis: 会话记忆、上下文缓存          │
│  - 内存: 运行时数据                     │
├─────────────────────────────────────────┤
│  External Dependencies                  │
│  - LangChain4j: LLM 抽象层             │
│  - PgVector: 向量数据库(可配置)         │
│  - MCP Tools: 工具协议                  │
└─────────────────────────────────────────┘
```

### 前端架构

```
┌─────────────────────────────────────────┐
│      Vue 3 + TypeScript + Vite         │
├─────────────────────────────────────────┤
│  Pages                                  │
│  - AgentChat.vue (主聊天界面)           │
├─────────────────────────────────────────┤
│  Components                             │
│  - AgentMessage (消息组件)              │
│  - AgentAssistant (助手动画)            │
│  - AgentSlide (会话列表)                │
│  - ModelSelector (模型选择)             │
│  - KnowledgeSelector (知识库选择)       │
│  - ToolConfig (工具配置)                │
│  - ProcessCard (执行过程卡片)           │
├─────────────────────────────────────────┤
│  Animations (Lottie)                    │
│  - idle.json, thinking.json, etc.       │
├─────────────────────────────────────────┤
│  Hooks                                  │
│  - useAgentChat (聊天逻辑)              │
├─────────────────────────────────────────┤
│  Services                               │
│  - agent.api.ts (API封装)               │
└─────────────────────────────────────────┘
```

## 核心设计决策

### 1. 数据存储方案

**不使用 MySQL，采用以下方案：**

- **会话数据**: Redis 存储，Key 格式 `agent:conversation:{id}`
- **消息历史**: Redis List/JSON，Key 格式 `agent:memory:{conversationId}`
- **上下文缓存**: Redis Hash，Key 格式 `agent:context:{conversationId}`
- **运行时数据**: 内存存储（Map、ConcurrentHashMap）

**Redis 数据结构示例：**
```json
// 会话信息
agent:conversation:conv-001 = {
  "id": "conv-001",
  "title": "关于设备配置的讨论",
  "createTime": "2025-01-01T10:00:00",
  "messageCount": 10,
  "status": "active"
}

// 消息历史 (List)
agent:memory:conv-001 = [
  {"role": "user", "content": "...", "timestamp": ...},
  {"role": "assistant", "content": "...", "timestamp": ...}
]

// 上下文缓存 (Hash)
agent:context:conv-001 = {
  "conversationId": "conv-001",
  "messages": [...],
  "toolCallHistory": [...],
  "ragRetrieveHistory": [...]
}
```

### 2. 配置管理

**使用 YAML 配置文件：**

```yaml
# application.yml
server:
  port: 8080

spring:
  redis:
    host: localhost
    port: 6379
    password: 
    database: 0

aiagent:
  model:
    default-model-id: "gpt-4o-mini"
    task-model-mapping:
      SIMPLE_CHAT: "gpt-4o-mini"
      RAG_QUERY: "gpt-4o-mini"
      TOOL_CALL: "gpt-4o"
      COMPLEX_WORKFLOW: "gpt-4o"
  
  memory:
    short-term-expire-hours: 24
    context-expire-hours: 1
    max-context-window: 20

  rag:
    default-top-k: 5
    default-min-score: 0.5

  tools:
    enabled-by-default: true
```

### 3. 模型配置

**支持两种方式：**

1. **配置文件方式** (轻量化优先)
```yaml
aiagent:
  models:
    - id: "gpt-4o-mini"
      name: "GPT-4o Mini"
      provider: "OPENAI"
      api-key: "${OPENAI_API_KEY}"
      model-name: "gpt-4o-mini"
```

2. **环境变量方式**
```bash
OPENAI_API_KEY=sk-xxx
DEEPSEEK_API_KEY=sk-xxx
```

### 4. 依赖最小化

**核心依赖（必需）：**
- Spring Boot Web (REST + SSE)
- Spring Data Redis
- LangChain4j (LLM 抽象)
- Jackson (JSON 处理)
- Lombok (简化代码)

**可选依赖：**
- PgVector (向量数据库，如需 RAG)
- MCP SDK (如需 MCP 工具)

## 项目结构

```
ai-agent-standalone/
├── backend/                          # 后端项目
│   ├── src/main/java/
│   │   └── com/aiagent/
│   │       ├── controller/          # 控制器
│   │       │   └── AgentController.java
│   │       ├── service/             # 服务层
│   │       │   ├── AgentService.java
│   │       │   ├── TaskClassifier.java
│   │       │   ├── RAGEnhancer.java
│   │       │   ├── ToolOrchestrator.java
│   │       │   └── MemorySystem.java
│   │       ├── config/              # 配置类
│   │       │   ├── RedisConfig.java
│   │       │   └── AgentConfig.java
│   │       ├── model/               # 领域模型
│   │       │   ├── TaskType.java
│   │       │   ├── MessageRole.java
│   │       │   └── ...
│   │       ├── vo/                  # 值对象
│   │       │   ├── AgentRequest.java
│   │       │   ├── AgentContext.java
│   │       │   └── ...
│   │       └── storage/             # 存储层
│   │           ├── ConversationStorage.java
│   │           └── MessageStorage.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── application-dev.yml
│   └── pom.xml
│
├── frontend/                         # 前端项目
│   ├── src/
│   │   ├── views/
│   │   │   └── agent/
│   │   │       ├── AgentChat.vue
│   │   │       ├── agent.api.ts
│   │   │       ├── agent.types.ts
│   │   │       ├── components/
│   │   │       │   ├── AgentMessage.vue
│   │   │       │   ├── AgentAssistant.vue
│   │   │       │   ├── AgentSlide.vue
│   │   │       │   ├── AgentModelSelector.vue
│   │   │       │   ├── AgentKnowledgeSelector.vue
│   │   │       │   ├── AgentToolConfig.vue
│   │   │       │   ├── ProcessCard.vue
│   │   │       │   └── animations/
│   │   │       │       ├── idle.json
│   │   │       │       ├── thinking.json
│   │   │       │       ├── hover.json
│   │   │       │       ├── click.json
│   │   │       │       └── drag.json
│   │   │       └── hooks/
│   │   │           └── useAgentChat.ts
│   │   ├── main.ts
│   │   └── App.vue
│   ├── package.json
│   └── vite.config.ts
│
├── docs/                             # 文档
│   ├── API.md                       # API 文档
│   └── QUICKSTART.md                # 快速开始
│
├── README.md                         # 项目说明
└── docker-compose.yml                # Docker 编排(可选)
```

## 核心功能保留

### 1. RAG 知识检索
- ✅ 向量数据库查询 (PgVector)
- ✅ 知识库检索增强
- ✅ 相关度过滤
- ✅ 提示词增强

### 2. MCP 工具调用
- ✅ 工具自动发现
- ✅ 工具智能选择
- ✅ 工具执行编排
- ✅ 结果解析总结

### 3. Agent Chat
- ✅ 流式对话 (SSE)
- ✅ 任务自动分类
- ✅ 多轮对话上下文
- ✅ 复杂任务编排
- ✅ 执行过程可视化

### 4. 记忆系统
- ✅ 短期记忆 (24小时)
- ✅ 工作记忆 (1小时)
- ✅ 对话历史管理
- ✅ 上下文缓存

## 移除的功能

- ❌ MySQL 数据库存储
- ❌ 多租户隔离 (简化为单租户)
- ❌ 用户权限管理 (可选，轻量化版本不包含)
- ❌ 平台级模型管理 (简化为配置管理)
- ❌ 对话持久化到数据库

## 技术栈

### 后端
- Java 17
- Spring Boot 2.7.18
- Spring Data Redis
- LangChain4j 0.35.0
- Lombok
- Jackson

### 前端
- Vue 3.3+
- TypeScript 5.0+
- Vite 5.0+
- Ant Design Vue 4.0+
- Lottie (动画)
- Pinia (状态管理)

### 存储
- Redis 6.0+ (必需)
- PgVector (可选，RAG功能需要)

## 部署方案

### 开发环境
```bash
# 启动 Redis
docker run -d -p 6379:6379 redis:7-alpine

# 启动后端
cd backend && mvn spring-boot:run

# 启动前端
cd frontend && pnpm dev
```

### 生产环境
```bash
# Docker Compose
docker-compose up -d

# 或单独部署
# 后端: 打包 jar 后 java -jar app.jar
# 前端: 构建后 nginx 静态部署
```

## 配置说明

### 最小配置示例

```yaml
# application.yml
server:
  port: 8080

spring:
  redis:
    host: localhost
    port: 6379

aiagent:
  model:
    default-model-id: "gpt-4o-mini"
  memory:
    short-term-expire-hours: 24
```

### 环境变量

```bash
# LLM API Keys
OPENAI_API_KEY=sk-xxx
DEEPSEEK_API_KEY=sk-xxx

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Vector DB (可选)
PGVECTOR_HOST=localhost
PGVECTOR_PORT=5432
PGVECTOR_DATABASE=vector_db
```

## 后续扩展

1. **可选持久化**: 支持导出会话到文件
2. **可观测性**: 集成 Prometheus + Grafana
3. **多用户支持**: 基于 JWT 的简单认证
4. **插件化**: 工具和模型插件化加载

## 迁移步骤

1. ✅ 创建项目目录结构
2. ✅ 剥离后端核心代码
3. ✅ 移除 MySQL 依赖
4. ✅ 实现 Redis 存储层
5. ✅ 剥离前端组件
6. ✅ 配置独立构建
7. ✅ 编写文档

## 注意事项

1. **数据持久化**: 会话数据存储在 Redis，重启后丢失，如需持久化需配置 Redis AOF
2. **向量数据库**: RAG 功能需要独立的 PgVector 数据库，可配置为可选
3. **API 密钥**: 模型 API 密钥通过环境变量或配置文件管理，注意安全性
4. **性能考虑**: 内存存储有大小限制，需合理配置过期时间

