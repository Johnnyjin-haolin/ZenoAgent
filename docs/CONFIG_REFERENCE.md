# 配置变量参考

本文档汇总 ZenoAgent 所有配置项，便于快速查阅和配置。

## 📋 配置方式说明

| 方式 | 适用场景 | 优先级 |
|------|----------|--------|
| **环境变量** | Docker 部署、生产环境 | 最高 |
| **Profile 配置** | `profile/{local\|prod\|test}/application.yml` | 中 |
| **主配置文件** | `application.yml` | 低 |
| **默认值** | 代码或 yml 中的默认 | 最低 |

**推荐用法：**
- **本地开发**：直接编辑 `backend/src/main/resources/profile/local/application.yml`
- **Docker 部署**：复制 `cp env.example .env`，编辑 `.env` 后执行 `docker-compose up -d`
- **传统部署**：编辑 `application.yml` 或 profile 配置，必要时用环境变量覆盖

完整配置模板见项目根目录 [env.example](../env.example)。

---

## 1. 必需配置（LLM API Key）

至少配置一个 LLM 提供商的 API Key，否则对话功能无法使用。

**两种配置方式：**

| 部署方式 | 说明 |
|----------|------|
| **Docker** | 使用 `profile/prod/application.yml`（已内置），通过 `apiKey: ${OPENAI_API_KEY:}` 从环境变量注入，在 `.env` 设置即可 |
| **本地开发** | 使用 `profile/local/application.yml`，直接填写 `apiKey: sk-xxx`，或使用 `apiKey: ${OPENAI_API_KEY:}` 从环境变量读取 |

`application.yml` 中 models 的 apiKey 为空，由 profile 提供。profile 覆盖主配置。

| 变量名 | 说明 | 默认值 | 必填 | 配置位置 |
|--------|------|--------|------|----------|
| `OPENAI_API_KEY` | OpenAI API 密钥（profile/prod 通过占位符注入） | - | 使用 OpenAI 时必填 | env / profile yml |
| `DEEPSEEK_API_KEY` | 深度求索 API 密钥（在 profile 中添加 DeepSeek 模型时可用 `${DEEPSEEK_API_KEY:}`） | - | 使用 DeepSeek 时必填 | env / profile yml |

---

## 2. Redis 配置

| 变量名 | 说明 | 默认值 | 必填 | 配置位置 |
|--------|------|--------|------|----------|
| `SPRING_REDIS_HOST` | Redis 主机地址 | localhost | 是 | env / application.yml |
| `SPRING_REDIS_PORT` | Redis 端口 | 6379 | 否 | env / application.yml |
| `SPRING_REDIS_PASSWORD` | Redis 密码 | 空 | 否 | env / application.yml |

Docker Compose 中 Redis 服务名为 `redis`，后端会自动使用该主机名。

---

## 3. MySQL 配置

| 变量名 | 说明 | 默认值 | 必填 | 配置位置 |
|--------|------|--------|------|----------|
| `SPRING_DATASOURCE_URL` | JDBC 连接 URL | 见下 | 是 | env / application.yml |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | root | 是 | env / application.yml |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | - | 是 | env / application.yml |
| `MYSQL_DATABASE` | 数据库名（Docker 用） | zeno_agent | 否 | env |
| `MYSQL_USER` | 应用用户名（Docker 用） | zenoagent | 否 | env |
| `MYSQL_PASSWORD` | 应用密码（Docker 用） | - | 否 | env |
| `MYSQL_ROOT_PASSWORD` | root 密码（Docker 用） | root123456 | 否 | env |
| `MYSQL_PORT` | MySQL 端口（Docker 用） | 3306 | 否 | env |

JDBC URL 示例：`jdbc:mysql://localhost:3306/zeno_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false`

Docker Compose 会根据 `MYSQL_*` 自动生成 `SPRING_DATASOURCE_*` 传给后端。

---

## 4. PostgreSQL + pgvector 配置（RAG 可选）

仅在启用 RAG 功能时需要。通过 `application.yml` 或 profile 中的 `aiagent.rag.embedding-store` 配置，也可用环境变量（若应用支持）。

| 变量名 | 说明 | 默认值 | 必填 | 配置位置 |
|--------|------|--------|------|----------|
| `PG_HOST` | PostgreSQL 主机 | localhost | RAG 启用时 | env / profile yml |
| `PG_PORT` | PostgreSQL 端口 | 5432 | 否 | env / profile yml |
| `PG_DATABASE` | 数据库名 | zeno_agent | 否 | env / profile yml |
| `PG_USER` | 用户名 | rag_user | 否 | env / profile yml |
| `PG_PASSWORD` | 密码 | - | 否 | env / profile yml |

RAG 配置示例见 [BACKEND_CONFIG.md](../BACKEND_CONFIG.md#-postgresql--pgvector-配置)。

---

## 5. 应用与部署配置

| 变量名 | 说明 | 默认值 | 必填 | 配置位置 |
|--------|------|--------|------|----------|
| `SPRING_PROFILES_ACTIVE` | 激活的 profile | local | 否 | env |
| `BACKEND_PORT` | 后端服务端口 | 8080 | 否 | env |
| `FRONTEND_PORT` | 前端服务端口 | 5173 | 否 | env |
| `AIAGENT_MODEL_DEFAULT_MODEL_ID` | 默认模型 ID | gpt-4o-mini | 否 | env / yml |

### 5.1 前端配置（VITE_*）

前端将 **构建时** 环境变量集中读入 `frontend/src/config/env.ts`，仅以 `VITE_` 开头的变量会被打包进前端资源。

| 变量名 | 说明 | 默认值 | 配置位置 |
|--------|------|--------|----------|
| `VITE_API_BASE_URL` | 前端请求后端 API 的基础地址 | 空（相对路径，与页面同源） | 构建前 .env / .env.production |
| `VITE_LOG_LEVEL` | 控制台日志级别 | 开发 debug，生产 error | 同上 |

- **同源部署**（前端与 Nginx 同域）：不设置 `VITE_API_BASE_URL` 或设为空，请求走相对路径（如 `/aiagent/execute`），由 Nginx 转发。
- **前后端不同域**：构建时设置 `VITE_API_BASE_URL=https://api.example.com` 等完整后端地址，并确保后端 CORS 允许该前端域名。
- Docker 构建前端时需通过 `--build-arg VITE_API_BASE_URL=...` 传入（若需覆盖默认）。

---

## 6. 快速配置示例

### Docker 部署

```bash
cp env.example .env
# 编辑 .env，至少设置 OPENAI_API_KEY 或 DEEPSEEK_API_KEY
# 按需修改 MYSQL_PASSWORD、MYSQL_ROOT_PASSWORD 等

docker-compose up -d
```

### 本地开发（非 Docker）

1. 复制 profile 配置：`cp backend/src/main/resources/profile/local/application.yml.example backend/src/main/resources/profile/local/application.yml`
2. 编辑 `profile/local/application.yml`，配置 Redis、MySQL、LLM API Key
3. 启动 Redis、MySQL 后运行：`cd backend && mvn spring-boot:run`

---

## 7. 相关文档

- [后端服务配置](../BACKEND_CONFIG.md) - Redis、MySQL、PostgreSQL 安装与配置
- [部署指南](../DEPLOYMENT.md) - 生产环境部署
- [env.example](../env.example) - 环境变量模板文件
