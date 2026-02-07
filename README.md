# ZenoAgent

<div align="center">

![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen.svg)
![Vue](https://img.shields.io/badge/Vue-3.3+-4FC08D.svg)
![TypeScript](https://img.shields.io/badge/TypeScript-5.0+-3178C6.svg)

**一个轻量化的 AI Agent 独立项目，支持 RAG、MCP、Agent Chat 等核心能力**

[功能特性](#-特性) • [快速开始](#-快速开始) • [文档](#-文档) • [部署指南](./DEPLOYMENT.md) • [贡献指南](./CONTRIBUTING.md)

</div>

---

> 一个轻量化的 AI Agent 独立项目，支持 RAG、MCP、Agent Chat 等核心能力，无需 MySQL 数据库。

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

### 🐳 使用 Docker 快速启动（推荐）

如果您想快速体验项目，可以使用 Docker Compose 一键启动：

```bash
# 1. 复制环境变量配置
cp .env.example .env
# 编辑 .env 文件，设置您的 API Key

# 2. 启动所有服务
./scripts/docker-start.sh
# 或使用 docker-compose
docker-compose up -d

# 3. 查看服务状态
docker-compose ps

# 4. 查看日志
docker-compose logs -f

# 5. 停止服务
docker-compose down
```

访问地址：
- 前端: http://localhost:5173
- 后端: http://localhost:8080

## 📖 文档

- [部署指南](./DEPLOYMENT.md) - 生产环境部署说明
- [贡献指南](./CONTRIBUTING.md) - 如何参与项目贡献
- [技术方案](./TECHNICAL_PLAN.md) - 详细的技术架构设计
- [API 文档](./docs/API.md) - API 接口文档

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

## 📸 截图

> 项目截图将在此处展示

## 🛠️ 开发

### 环境要求

- Java 17+
- Maven 3.6+
- Node.js 20+
- pnpm 9+
- Redis 6.0+

### 本地开发

```bash
# 克隆项目
git clone https://github.com/your-org/ZenoAgent.git
cd ZenoAgent

# 启动 Redis
docker run -d -p 6379:6379 redis:7-alpine

# 启动后端
cd backend
mvn spring-boot:run

# 启动前端（新终端）
cd frontend
pnpm install
pnpm dev
```

### 构建

```bash
# 构建后端
cd backend
mvn clean package

# 构建前端
cd frontend
pnpm build
```

## 📄 许可证

本项目采用 [Apache License 2.0](./LICENSE) 开源协议。

## 🤝 贡献

我们欢迎所有形式的贡献！请查看 [贡献指南](./CONTRIBUTING.md) 了解详细信息。

- 🐛 [报告 Bug](https://github.com/your-org/ZenoAgent/issues)
- 💡 [提出功能建议](https://github.com/your-org/ZenoAgent/issues)
- 📝 [提交 Pull Request](https://github.com/your-org/ZenoAgent/pulls)

## ⭐ Star History

如果这个项目对您有帮助，请给我们一个 Star ⭐

## 📞 联系我们

- 提交 Issue: [GitHub Issues](https://github.com/your-org/ZenoAgent/issues)
- 讨论: [GitHub Discussions](https://github.com/your-org/ZenoAgent/discussions)

## 🙏 致谢

感谢所有为这个项目做出贡献的开发者！

---

<div align="center">
Made with ❤️ by ZenoAgent Team
</div>

