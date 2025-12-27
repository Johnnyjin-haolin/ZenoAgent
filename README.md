# AI Agent 轻量化独立项目

> 从 JeecG Boot 平台剥离的 AI Agent 模块，轻量化设计，不使用 MySQL，保留 RAG、MCP、Agent Chat 核心能力。

## ✨ 特性

- 🤖 **智能对话**: 支持多轮对话，自动维护上下文
- 📚 **知识检索**: 集成 RAG 技术，从向量数据库检索相关知识
- 🔧 **工具调用**: 通过 MCP 协议调用各种工具和服务
- 🎯 **任务编排**: 自动规划和执行复杂的多步骤任务
- 🌊 **流式响应**: SSE 实时推送执行过程和结果
- 💾 **记忆管理**: Redis 存储短期和长期记忆
- 📦 **轻量化**: 无 MySQL 依赖，仅需 Redis

## 📋 技术栈

### 后端
- Java 17
- Spring Boot 2.7.18
- Spring Data Redis
- LangChain4j 0.35.0

### 前端
- Vue 3.3+
- TypeScript 5.0+
- Vite 5.0+
- Ant Design Vue 4.0+
- Lottie (动画)

### 存储
- Redis 6.0+ (必需)
- PgVector (可选，RAG功能需要)

## 🚀 快速开始

### 前置要求

1. **Java 17+**
2. **Node.js 20+** 和 **pnpm 9+**
3. **Redis 6.0+** (运行中)
4. **PgVector** (可选，如需 RAG 功能)

### 1. 启动 Redis

```bash
docker run -d -p 6379:6379 redis:7-alpine
```

### 2. 配置后端

复制配置文件模板：

```bash
cd backend/src/main/resources
cp application.yml.example application.yml
```

编辑 `application.yml`，配置 Redis 和模型 API Key：

```yaml
spring:
  redis:
    host: localhost
    port: 6379

aiagent:
  model:
    default-model-id: "gpt-4o-mini"
  models:
    - id: "gpt-4o-mini"
      name: "GPT-4o Mini"
      provider: "OPENAI"
      api-key: "${OPENAI_API_KEY}"
      model-name: "gpt-4o-mini"
```

设置环境变量：

```bash
export OPENAI_API_KEY=sk-xxx
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端服务将在 `http://localhost:8080` 启动。

### 4. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

前端服务将在 `http://localhost:5173` 启动。

## 📖 文档

- [技术方案](./TECHNICAL_PLAN.md) - 详细的技术架构设计
- [API 文档](./docs/API.md) - API 接口文档
- [快速开始](./docs/QUICKSTART.md) - 快速上手指南

## 🏗️ 项目结构

```
ai-agent-standalone/
├── backend/              # 后端项目
│   ├── src/main/java/com/aiagent/
│   │   ├── controller/   # 控制器
│   │   ├── service/      # 服务层
│   │   ├── config/       # 配置类
│   │   ├── model/        # 领域模型
│   │   ├── vo/           # 值对象
│   │   └── storage/      # 存储层
│   └── src/main/resources/
│       └── application.yml
│
├── frontend/             # 前端项目
│   ├── src/views/agent/  # Agent 页面和组件
│   └── package.json
│
└── docs/                 # 文档
```

## 🔧 配置说明

### 最小配置

只需配置 Redis 连接和 LLM API Key：

```yaml
spring:
  redis:
    host: localhost
    port: 6379

aiagent:
  model:
    default-model-id: "gpt-4o-mini"
```

### 环境变量

```bash
# LLM API Keys
OPENAI_API_KEY=sk-xxx
DEEPSEEK_API_KEY=sk-xxx

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
```

## 💡 核心功能

### 1. RAG 知识检索
- 向量数据库查询 (PgVector)
- 知识库检索增强
- 相关度过滤
- 提示词增强

### 2. MCP 工具调用
- 工具自动发现
- 工具智能选择
- 工具执行编排
- 结果解析总结

### 3. Agent Chat
- 流式对话 (SSE)
- 任务自动分类
- 多轮对话上下文
- 复杂任务编排
- 执行过程可视化

## ⚠️ 注意事项

1. **数据持久化**: 会话数据存储在 Redis，重启后可能丢失，建议配置 Redis AOF
2. **向量数据库**: RAG 功能需要独立的 PgVector 数据库，可配置为可选
3. **API 密钥**: 模型 API 密钥通过环境变量管理，注意安全性

## 📝 开发计划

- [x] 项目结构创建
- [x] 后端核心代码剥离
- [x] 前端组件剥离
- [ ] Redis 存储层实现
- [ ] 配置文件完善
- [ ] API 文档编写
- [ ] 使用文档编写

## 📄 许可证

本项目采用 Apache License 2.0 开源协议。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

