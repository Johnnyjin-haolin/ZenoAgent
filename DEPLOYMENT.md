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

详细的后端服务配置说明请参考 [后端服务配置文档](./BACKEND_CONFIG.md)。

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
   cp .env.example .env
   ```
   
   编辑 `.env` 文件，设置必要的环境变量：
   ```env
   # LLM API Keys
   OPENAI_API_KEY=sk-your-openai-key
   DEEPSEEK_API_KEY=sk-your-deepseek-key
   
   # Redis
   REDIS_HOST=redis
   REDIS_PORT=6379
   
   # Backend
   BACKEND_PORT=8080
   SPRING_PROFILES_ACTIVE=prod
   
   # Frontend
   FRONTEND_PORT=5173
   VITE_API_BASE_URL=http://localhost:8080
   ```

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

```sql
CREATE DATABASE IF NOT EXISTS zeno_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE zeno_agent;
```

#### 表结构定义

**1. agent_conversation 表（Agent会话表）**

```sql
CREATE TABLE agent_conversation
(
    id            VARCHAR(64)                            NOT NULL COMMENT '会话ID（UUID）'
        PRIMARY KEY,
    title         VARCHAR(255) DEFAULT '新对话'          NOT NULL COMMENT '会话标题',
    user_id       VARCHAR(64)                            NULL COMMENT '用户ID（预留）',
    model_id      VARCHAR(64)                            NULL COMMENT '使用的模型ID',
    model_name    VARCHAR(128)                           NULL COMMENT '模型名称',
    status        VARCHAR(32)  DEFAULT 'active'          NOT NULL COMMENT '状态：active/archived/deleted',
    message_count INT          DEFAULT 0                 NOT NULL COMMENT '消息数量',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
)
    COMMENT 'Agent会话表' COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_status ON agent_conversation (status);
CREATE INDEX idx_update_time ON agent_conversation (update_time);
CREATE INDEX idx_user_id ON agent_conversation (user_id);
```

**2. agent_message 表（Agent消息表）**

```sql
CREATE TABLE agent_message
(
    id              BIGINT AUTO_INCREMENT COMMENT '消息ID'
        PRIMARY KEY,
    conversation_id VARCHAR(64)                        NOT NULL COMMENT '会话ID',
    message_id      VARCHAR(64)                        NOT NULL COMMENT '消息唯一标识（UUID）',
    role            VARCHAR(32)                        NOT NULL COMMENT '角色：user/assistant/system',
    content         TEXT                               NOT NULL COMMENT '消息内容',
    model_id        VARCHAR(64)                        NULL COMMENT '使用的模型ID',
    tokens          INT                                NULL COMMENT 'Token数量',
    duration        INT                                NULL COMMENT '耗时（毫秒）',
    metadata        JSON                               NULL COMMENT '元数据（工具调用、RAG结果等）',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    CONSTRAINT fk_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES agent_conversation (id)
            ON DELETE CASCADE
)
    COMMENT 'Agent消息表' COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_conversation_id ON agent_message (conversation_id);
CREATE INDEX idx_create_time ON agent_message (create_time);
CREATE INDEX idx_message_id ON agent_message (message_id);
```

**3. knowledge_base 表（知识库表）**

```sql
CREATE TABLE knowledge_base
(
    id                 VARCHAR(64)                         NOT NULL
        PRIMARY KEY,
    name               VARCHAR(255)                        NOT NULL,
    description        TEXT                                NULL,
    embedding_model_id VARCHAR(255)                        NOT NULL,
    create_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL
);
```

**4. document 表（文档表）**

```sql
CREATE TABLE document
(
    id                VARCHAR(64)                         NOT NULL
        PRIMARY KEY,
    knowledge_base_id VARCHAR(64)                         NOT NULL,
    title             VARCHAR(255)                        NOT NULL,
    type              VARCHAR(50)                         NOT NULL,
    content           TEXT                                NULL,
    metadata          TEXT                                NULL,
    status            VARCHAR(50)                         NOT NULL,
    create_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    update_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
    CONSTRAINT document_ibfk_1
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base (id)
            ON DELETE CASCADE
);

CREATE INDEX knowledge_base_id ON document (knowledge_base_id);
```

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

    # API 代理
    location /aiagent {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # SSE 支持
    location /aiagent/stream {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding off;
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

## 📞 获取帮助

如果遇到部署问题，请：

1. 查看 [常见问题](./docs/FAQ.md)
2. 提交 [Issue](https://github.com/your-org/ZenoAgent/issues)
3. 查看日志文件
