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

创建 Nginx 配置 `/etc/nginx/sites-available/zenoagent`：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /opt/zenoagent/frontend/dist;
    index index.html;

    # 前端路由
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Agent 执行接口（SSE 流式）：必须关闭缓冲，否则流式响应会卡住
    location /aiagent/execute {
        proxy_pass http://localhost:8080;
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
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

启用配置：
```bash
sudo ln -s /etc/nginx/sites-available/zenoagent /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
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

## 📞 获取帮助

如果遇到部署问题，请：

1. 查看 [常见问题](./docs/FAQ.md)
2. 提交 [Issue](https://github.com/your-org/ZenoAgent/issues)
3. 查看日志文件
