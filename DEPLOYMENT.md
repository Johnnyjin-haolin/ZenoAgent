# 部署指南

本文档介绍如何部署 ZenoAgent 到生产环境。

## 📋 前置要求

### 系统要求

- **操作系统**: Linux (推荐 Ubuntu 20.04+ / CentOS 7+)
- **Java**: 17+
- **Node.js**: 20+
- **Redis**: 6.0+
- **内存**: 至少 2GB RAM
- **磁盘**: 至少 10GB 可用空间

### 依赖服务

- Redis 6.0+ (必需)
- MySQL 8.0+ (必需，用于持久化存储)
- PostgreSQL with pgvector (可选，RAG 功能需要)
- Playwright 浏览器 (可选，Web Search 高级搜索功能需要)

详细的后端服务配置说明请参考 [后端服务配置文档](./BACKEND_CONFIG.md)，配置项一览见 [配置变量参考](./docs/CONFIG_REFERENCE.md)。

## 🐳 Docker 部署（推荐）

### 使用 Docker Compose

1. **克隆项目**
   ```bash
   git clone https://github.com/your-org/ZenoAgent.git
   cd ZenoAgent
   ```

2. **配置环境变量**
   
   创建 `.env` 文件：
   ```bash
   cp env.example .env
   ```
   
   编辑 `.env` 文件，设置必要的环境变量。**所有配置项说明**见 [配置变量参考](./docs/CONFIG_REFERENCE.md)，至少需配置：

3. **启动服务**
   ```bash
   docker-compose up -d
   ```

4. **查看日志**
   ```bash
   docker-compose logs -f
   ```

5. **停止服务**
   ```bash
   docker-compose down
   ```

### 单独构建 Docker 镜像

#### 后端镜像

```bash
cd backend
docker build -t zenoagent-backend:latest .
docker run -d \
  -p 8080:8080 \
  -e OPENAI_API_KEY=sk-xxx \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  --name zenoagent-backend \
  zenoagent-backend:latest
```

#### 前端镜像

```bash
cd frontend
docker build -t zenoagent-frontend:latest .
docker run -d \
  -p 5173:80 \
  -e VITE_API_BASE_URL=http://your-backend-url:8080 \
  --name zenoagent-frontend \
  zenoagent-frontend:latest
```

## 💾 数据库初始化

### MySQL 数据库表结构

ZenoAgent 使用 MySQL 存储会话、消息、知识库和文档等持久化数据。首次部署前需要创建数据库和表结构。

#### 创建数据库
 [数据库初始化脚本](./backend/src/main/resources/sql/init.sql)。


#### 快速初始化

您可以使用项目提供的初始化脚本：

```bash
# 方式1：使用 MySQL 命令行
mysql -u root -p < backend/src/main/resources/sql/init.sql

# 方式2：在 MySQL 客户端中执行
mysql -u root -p
source backend/src/main/resources/sql/init.sql
```

### PostgreSQL 数据库初始化（RAG 功能需要）

如果使用 RAG 功能，需要配置 PostgreSQL 并安装 pgvector 扩展。详细配置请参考 [后端服务配置文档](./BACKEND_CONFIG.md)。

## 🚀 传统部署

### 1. 部署 Redis

#### 使用 Docker（推荐）
```bash
docker run -d \
  --name redis \
  -p 6379:6379 \
  -v redis-data:/data \
  redis:7-alpine \
  redis-server --appendonly yes
```

#### 使用包管理器
```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install redis-server

# CentOS/RHEL
sudo yum install redis
sudo systemctl start redis
sudo systemctl enable redis
```

### 2. 部署后端

#### 构建 JAR 包
```bash
cd backend
mvn clean package -DskipTests
```

#### 安装 Playwright 浏览器（可选）

如果需要使用 **Web Search 高级搜索功能**（真实浏览器搜索，支持 JS 渲染、Cookie 持久化、反爬虫检测），需要安装 Playwright 浏览器：

**方式一：使用 Maven 插件自动安装（推荐）**

```bash
cd backend
# 安装 Playwright 浏览器（Chromium）
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

**方式二：手动下载安装**

```bash
# 下载 Playwright CLI JAR
wget https://repo1.maven.org/maven2/com/microsoft/playwright/playwright/1.44.0/playwright-1.44.0.jar

# 安装 Chromium 浏览器
java -jar playwright-1.44.0.jar install chromium

# 安装所有浏览器（可选，包括 Chromium、Firefox、WebKit）
java -jar playwright-1.44.0.jar install
```

**方式三：使用 Playwright 命令行**

```bash
# 如果系统已安装 Node.js，可使用 npx
npx playwright install chromium
```

**Docker 环境下安装**

在 Dockerfile 中添加 Playwright 安装：

```dockerfile
# 在运行阶段添加 Playwright 依赖
RUN apk add --no-cache \
    chromium \
    nss \
    freetype \
    harfbuzz \
    ttf-freefont

# 设置环境变量指向系统 Chromium
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV CHROMIUM_PATH=/usr/bin/chromium-browser
```

**验证安装**

```bash
# 检查 Playwright 浏览器是否安装成功
java -jar playwright-1.44.0.jar --help
```

> **注意**：Playwright 浏览器约需 **300-500MB** 磁盘空间。如果不需要 Web Search 高级功能，可在配置中将 `aiagent.tools.web-search.engine` 设置为 `http`（使用轻量级 HTTP 请求），无需安装浏览器。

#### 运行 JAR 包
```bash
java -jar target/ai-agent-standalone-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.redis.host=localhost \
  --spring.redis.port=6379
```

#### 使用 systemd 服务（推荐）

创建服务文件 `/etc/systemd/system/zenoagent.service`：

```ini
[Unit]
Description=ZenoAgent Backend Service
After=network.target redis.service

[Service]
Type=simple
User=zenoagent
WorkingDirectory=/opt/zenoagent/backend
ExecStart=/usr/bin/java -jar /opt/zenoagent/backend/target/ai-agent-standalone-1.0.0.jar --spring.profiles.active=prod
Restart=always
RestartSec=10
Environment="OPENAI_API_KEY=sk-xxx"
Environment="REDIS_HOST=localhost"
Environment="REDIS_PORT=6379"

[Install]
WantedBy=multi-user.target
```

启动服务：
```bash
sudo systemctl daemon-reload
sudo systemctl enable zenoagent
sudo systemctl start zenoagent
sudo systemctl status zenoagent
```

### 3. 部署前端

前端运行时常量（如 API 地址、日志级别）集中在 **`frontend/src/config/env.ts`**，由构建时环境变量 `VITE_*` 注入。构建前可按需在项目根目录或 `frontend` 下配置 `.env` 或 `.env.production`，例如：

```bash
# 与 Nginx 同源部署时可不设置（使用相对路径 /aiagent/...）
# VITE_API_BASE_URL=

# 前端与后端不同域时填写后端完整地址
# VITE_API_BASE_URL=https://api.example.com

# 可选：生产环境日志级别 debug | info | warn | error | none
# VITE_LOG_LEVEL=error
```

详见 [配置变量参考](./docs/CONFIG_REFERENCE.md) 中的「前端配置」小节。

#### 构建生产版本
```bash
cd frontend
pnpm install
pnpm build
```

#### 使用 Nginx 部署

SSE 流式接口 `/aiagent/execute` 必须单独配置：关闭缓冲并拉长超时，否则 Nginx 默认 `proxy_read_timeout` 为 60s，会导致约 60 秒断连、前端报错或后端出现 `Broken pipe`。以下为已验证可用的配置。

创建或修改 Nginx 配置（如 `/etc/nginx/sites-available/zenoagent` 或宝塔面板中对应站点的配置）：

```nginx
server {
    listen 80;
    server_name zeno-agent;   # 或 your-domain.com，按实际域名修改

    root /www/wwwroot/ZenoAgent/front/dist;   # 或 /opt/zenoagent/frontend/dist，按实际路径修改
    index index.html;

    # 前端路由
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Agent 执行接口（SSE 流式）：必须单独配置，否则默认 60s 断连
    location /aiagent/execute {
        proxy_pass http://127.0.0.1:8080;   # Nginx 与后端同机用 127.0.0.1，否则改为后端地址如 http://1.2.3.4:8080
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding off;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }

    # 其余 API 代理
    location /aiagent {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 120s;
        proxy_send_timeout 120s;
    }
}
```

说明：
- **`/aiagent/execute`** 必须单独成块并设置 `proxy_read_timeout`/`proxy_send_timeout`（如 300s），否则会按默认 60s 断连。
- 不要为 SSE 使用 `location /aiagent/stream` 而漏配 `/aiagent/execute`，实际流式接口是 **`/aiagent/execute`**。

启用或重载配置：
```bash
sudo nginx -t
sudo nginx -s reload
# 或 systemctl reload nginx
```

#### 使用 PM2 部署（Node.js 环境）

```bash
npm install -g pm2
cd frontend
pnpm build
pm2 serve dist 5173 --spa
pm2 save
pm2 startup
```

## 🔒 安全配置

### 1. 使用 HTTPS

使用 Let's Encrypt 配置 SSL：

```bash
sudo apt-get install certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

### 2. 防火墙配置

```bash
# 允许 HTTP/HTTPS
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# 仅允许本地访问 Redis
sudo ufw deny 6379/tcp
```

### 3. 环境变量安全

- 使用密钥管理服务（如 AWS Secrets Manager、HashiCorp Vault）
- 不要在代码中硬编码密钥
- 使用 `.env` 文件并确保不被提交到 Git

## 📊 监控和日志

### 应用日志

后端日志位置：
- 默认：控制台输出
- 配置日志文件：在 `application.yml` 中配置 `logging.file.path`

### 健康检查

后端健康检查端点：
```bash
curl http://localhost:8080/actuator/health
```

### 监控建议

- 使用 Prometheus + Grafana 监控应用指标
- 使用 ELK Stack 收集和分析日志
- 配置告警规则（CPU、内存、错误率等）

## 🔄 更新部署

### 更新后端

```bash
# 停止服务
sudo systemctl stop zenoagent

# 备份当前版本
cp target/ai-agent-standalone-1.0.0.jar target/ai-agent-standalone-1.0.0.jar.bak

# 更新代码并构建
git pull
mvn clean package -DskipTests

# 启动服务
sudo systemctl start zenoagent
```

### 更新前端

```bash
cd frontend
git pull
pnpm install
pnpm build
sudo systemctl reload nginx
```

## 🐛 故障排查

### 后端无法启动

1. 检查 Java 版本：`java -version`
2. 检查端口占用：`netstat -tulpn | grep 8080`
3. 查看日志：`journalctl -u zenoagent -f`

### Redis 连接失败

1. 检查 Redis 是否运行：`redis-cli ping`
2. 检查防火墙规则
3. 验证 Redis 配置

### Playwright 浏览器初始化失败

**症状**：启动日志中出现 `[PlaywrightPool] Playwright 初始化失败，将降级使用 HTTP 模式`

**常见原因及解决方案**：

| 原因 | 解决方案 |
|------|----------|
| 浏览器未安装 | 执行 `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"` 安装 Chromium |
| Docker 容器缺少依赖 | 在 Dockerfile 中添加 `chromium`、`nss`、`freetype`、`harfbuzz` 等依赖包 |
| 内存不足 | 确保系统至少有 2GB 可用内存，Playwright 浏览器运行需要一定内存 |
| 权限问题 | 确保 `playwright-data` 目录有读写权限：`chmod -R 755 ./playwright-data` |

**验证 Playwright 是否正常工作**：

```bash
# 测试 Playwright 安装
java -cp target/ai-agent-standalone-1.0.0.jar com.microsoft.playwright.CLI --help

# 或使用 Maven
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="--help"
```

**降级方案**：如果无法安装 Playwright，可在配置中将搜索引擎切换为 HTTP 模式：

```yaml
aiagent:
  tools:
    web-search:
      engine: http  # 使用轻量级 HTTP 请求，无需浏览器
```

**Docker 环境特殊配置**：

Docker 容器中运行 Playwright 需要额外的系统依赖和配置：

```dockerfile
# 使用支持 Playwright 的基础镜像（推荐）
FROM mcr.microsoft.com/playwright/java:v1.44.0-jammy

# 或在 Alpine 中安装依赖
RUN apk add --no-cache \
    chromium \
    nss \
    freetype \
    harfbuzz \
    ttf-freefont \
    dbus \
    xvfb

# 设置环境变量
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV CHROMIUM_PATH=/usr/bin/chromium-browser
```

```yaml
# application.yml 配置（Docker 环境）
aiagent:
  tools:
    web-search:
      engine: playwright
      headless: true  # Docker 中必须为 true
      user-data-dir: /app/playwright-data  # 容器内路径
```

### 前端无法访问后端

1. 检查 CORS 配置
2. 检查 Nginx 代理配置
3. 检查后端服务是否运行

### 前端报错 "Agent 执行失败: TypeError: Failed to fetch"

**原因说明**：浏览器发起的请求没有到达后端。常见有两种情况：

1. **API 地址指向了错误的主机（最常见）**  
   前端未设置 `VITE_API_BASE_URL` 时，旧版本默认请求 `http://localhost:8080`。在用户浏览器里，“localhost” 是用户本机，不是服务器，因此会报 `Failed to fetch`。

2. **Nginx 未对 SSE 接口关闭缓冲**  
   `/aiagent/execute` 是 SSE 流式接口，若 Nginx 开启缓冲，可能导致连接异常或超时。

**排查步骤：**

| 步骤 | 操作 |
|------|------|
| 1 | 在浏览器开发者工具 → Network，找到执行 Agent 时的请求，看请求 URL。若为 `http://localhost:8080/aiagent/execute`，说明前端仍在使用绝对后端地址，需按下方“解决方案”处理。 |
| 2 | 若请求 URL 为相对路径（如 `/aiagent/execute`）或与当前页面同域，再看该请求的状态码：4xx/5xx 表示 Nginx 或后端异常；若为 CORS 错误，检查后端 CORS 配置。 |
| 3 | 在服务器上执行：`curl -X POST http://localhost:8080/aiagent/execute -H "Content-Type: application/json" -d '{"content":"hi","conversationId":"1","agentId":"1","mode":"MANUAL","modelId":"1"}'`，确认本机直连后端是否正常。 |
| 4 | 检查 Nginx 配置是否对 `/aiagent/execute` 单独配置了 `proxy_buffering off` 等 SSE 相关项（见上文“使用 Nginx 部署”示例）。 |

**解决方案：**

- **前端同源部署（推荐）**：前端与 Nginx 同域时，构建时不要设置 `VITE_API_BASE_URL`，或设为空。前端会使用相对路径（如 `/aiagent/execute`），由 Nginx 转发到后端。重新构建并部署：`cd frontend && pnpm build`。
- **前端与后端不同域**：构建时设置 `VITE_API_BASE_URL` 为后端完整地址（如 `https://api.example.com`），并确保后端允许该域的 CORS。
- **Nginx**：按本文“使用 Nginx 部署”一节，为 `/aiagent/execute` 单独添加 `location`，并设置 `proxy_buffering off`、`proxy_cache off`、合理 `proxy_read_timeout`，然后 `nginx -t && systemctl reload nginx`。

### 约 60 秒断连或后端报 "Broken pipe"

若前端约 60 秒后断连、或后端日志出现 `SSE连接错误: Broken pipe` / `ClientAbortException: java.io.IOException: Broken pipe`，多半是 Nginx 未对 `/aiagent/execute` 单独配置，使用了默认的 **60 秒** `proxy_read_timeout`。解决：按本文「使用 Nginx 部署」为 `/aiagent/execute` 单独写 `location`，并设置 `proxy_read_timeout 300s`、`proxy_send_timeout 300s`。详见 [SSE 报错分析](./docs/SSE_ERR_INCOMPLETE_CHUNKED_ENCODING.md)。

## 📞 获取帮助

如果遇到部署问题，请：

1. 查看 [常见问题](./docs/FAQ.md)
2. 提交 [Issue](https://github.com/your-org/ZenoAgent/issues)
3. 查看日志文件
