# 后端服务配置指南

本文档介绍 ZenoAgent 后端所需的服务配置，包括 Redis、MySQL 和 PostgreSQL (pgvector) 的安装和配置说明。

## 📋 概述

ZenoAgent 后端依赖以下服务：

- **Redis** (必需): 用于会话上下文、短期记忆缓存
- **MySQL** (必需): 用于会话、消息、知识库、文档等持久化存储
- **PostgreSQL + pgvector** (可选): 用于向量存储，仅在启用 RAG 功能时需要

## 🔴 Redis 配置

### 作用说明

Redis 在 ZenoAgent 中用于：
- 存储会话上下文和短期记忆
- 缓存对话状态
- 分布式锁和队列（使用 Redisson）

### 安装 Redis

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

**Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install redis-server
sudo systemctl start redis
sudo systemctl enable redis
```

**CentOS/RHEL:**
```bash
sudo yum install redis
sudo systemctl start redis
sudo systemctl enable redis
```

**macOS:**
```bash
brew install redis
brew services start redis
```

### 配置说明

在 `application.yml` 中配置 Redis 连接：

```yaml
spring:
  redis:
    host: localhost        # Redis 主机地址
    port: 6379            # Redis 端口
    password:             # Redis 密码（如果设置了密码）
    database: 0           # 使用的数据库编号（0-15）
    timeout: 5000ms       # 连接超时时间
    lettuce:
      pool:
        max-active: 8     # 最大连接数
        max-idle: 8       # 最大空闲连接数
        min-idle: 0       # 最小空闲连接数
```

### 环境变量配置

也可以通过环境变量配置：

```bash
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=your_password  # 可选
```

### 验证连接

```bash
# 测试 Redis 连接
redis-cli ping
# 应该返回: PONG

# 查看 Redis 信息
redis-cli info
```

## 🟢 MySQL 配置

### 作用说明

MySQL 在 ZenoAgent 中用于：
- 持久化存储会话（agent_conversation）
- 持久化存储消息（agent_message）
- 持久化存储知识库（knowledge_base）
- 持久化存储文档（document）

### 安装 MySQL

#### 使用 Docker（推荐）

```bash
docker run -d \
  --name mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=your_password \
  -e MYSQL_DATABASE=zeno_agent \
  -v mysql-data:/var/lib/mysql \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci
```

#### 使用包管理器

**Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install mysql-server
sudo systemctl start mysql
sudo systemctl enable mysql
```

**CentOS/RHEL:**
```bash
sudo yum install mysql-server
sudo systemctl start mysqld
sudo systemctl enable mysqld
```

**macOS:**
```bash
brew install mysql
brew services start mysql
```

### 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS zeno_agent 
  DEFAULT CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;
```

### 配置说明

在 `application.yml` 中配置 MySQL 连接：

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/zeno_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: your_password
    druid:
      initial-size: 5      # 初始连接数
      min-idle: 5          # 最小空闲连接数
      max-active: 20       # 最大连接数
      max-wait: 60000      # 获取连接最大等待时间（毫秒）
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
```

### 环境变量配置

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_DATABASE=zeno_agent
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=your_password
```

### 初始化表结构

执行初始化脚本创建表结构：

```bash
# 方式1：使用 MySQL 命令行
mysql -u root -p zeno_agent < backend/src/main/resources/sql/init.sql

# 方式2：在 MySQL 客户端中执行
mysql -u root -p
USE zeno_agent;
SOURCE backend/src/main/resources/sql/init.sql;
```

详细的表结构定义请参考 [部署文档](./DEPLOYMENT.md#-数据库初始化)。

### 验证连接

```bash
# 测试 MySQL 连接
mysql -u root -p -e "SHOW DATABASES;"

# 检查表是否创建成功
mysql -u root -p zeno_agent -e "SHOW TABLES;"
```

## 🔵 PostgreSQL + pgvector 配置

### 作用说明

**重要提示**: PostgreSQL + pgvector 仅在启用 RAG（检索增强生成）功能时需要配置。如果不需要 RAG 功能，可以跳过此配置。

PostgreSQL + pgvector 在 ZenoAgent 中用于：
- 存储文档的向量嵌入（embeddings）
- 执行向量相似度搜索
- 支持 RAG 知识检索功能

### 安装 PostgreSQL with pgvector

#### 使用 Docker（推荐，包含 pgvector）

```bash
# 使用 pgvector 官方镜像（推荐）
docker run -d \
  --name postgres \
  -p 5432:5432 \
  -e POSTGRES_USER=rag_user \
  -e POSTGRES_PASSWORD=Rag@123456 \
  -e POSTGRES_DB=zeno_agent \
  -v postgres-data:/var/lib/postgresql/data \
  pgvector/pgvector:pg16
```

#### 手动安装 PostgreSQL 和 pgvector

**1. 安装 PostgreSQL**

**Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install postgresql postgresql-contrib
```

**CentOS/RHEL:**
```bash
sudo yum install postgresql postgresql-server
sudo postgresql-setup initdb
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**macOS:**
```bash
brew install postgresql
brew services start postgresql
```

**2. 安装 pgvector 扩展**

**从源码编译安装:**
```bash
# 安装依赖
sudo apt-get install build-essential git postgresql-server-dev-14

# 克隆 pgvector 仓库
git clone --branch v0.5.1 https://github.com/pgvector/pgvector.git
cd pgvector

# 编译安装
make
sudo make install
```

**使用包管理器（如果可用）:**
```bash
# Ubuntu/Debian (如果仓库中有)
sudo apt-get install postgresql-14-pgvector
```

### 创建数据库和启用扩展

```bash
# 连接到 PostgreSQL
psql -U postgres

# 创建数据库
CREATE DATABASE zeno_agent;

# 连接到新数据库
\c zeno_agent

# 启用 pgvector 扩展
CREATE EXTENSION vector;

# 验证扩展是否安装成功
\dx
# 应该看到 vector 扩展
```

### 配置说明

在 `application.yml` 中配置 PostgreSQL 连接：

```yaml
aiagent:
  rag:
    # 向量存储配置（PostgreSQL + pgvector）
    # 注意：RAG功能需要PostgreSQL数据库并安装pgvector扩展
    embedding-store:
      host: localhost          # PostgreSQL 主机地址
      port: 5432               # PostgreSQL 端口
      database: zeno_agent     # 数据库名称
      user: rag_user           # 数据库用户名
      password: Rag@123456     # 数据库密码
      table: embeddings        # 向量存储表名（会自动创建）
      use-index: false         # 是否使用索引（2560维向量超过ivfflat索引的2000维限制）
      index-list-size: 100     # 索引列表大小
```

### 环境变量配置

```bash
export PG_HOST=localhost
export PG_PORT=5432
export PG_DATABASE=zeno_agent
export PG_USER=rag_user
export PG_PASSWORD=Rag@123456
```

### 验证连接和扩展

```bash
# 测试 PostgreSQL 连接
psql -U rag_user -d zeno_agent -c "SELECT version();"

# 检查 pgvector 扩展
psql -U rag_user -d zeno_agent -c "\dx"
# 应该看到 vector 扩展

# 测试向量功能
psql -U rag_user -d zeno_agent -c "SELECT '[1,2,3]'::vector;"
```

### 注意事项

1. **向量维度限制**: 
   - pgvector 的 ivfflat 索引支持的最大维度是 2000
   - 如果使用超过 2000 维的向量模型（如 text-embedding-3-large 的 3072 维），需要设置 `use-index: false`
   - 当前配置已默认禁用索引以支持高维向量

2. **性能优化**:
   - 对于生产环境，建议根据实际向量维度选择合适的索引策略
   - 如果向量维度 ≤ 2000，可以启用索引以提高查询性能

3. **RAG 功能可选**:
   - 如果不需要 RAG 功能，可以不配置 PostgreSQL
   - 系统会在 RAG 功能被调用时检查配置，未配置时会给出提示

## 🔧 配置文件位置

所有配置都在 `backend/src/main/resources/application.yml` 中，或者通过环境变量覆盖。

### Profile 配置

项目支持多环境配置，配置文件位于：
- `backend/src/main/resources/profile/local/application.yml` - 本地开发环境
- `backend/src/main/resources/profile/prod/application.yml` - 生产环境
- `backend/src/main/resources/profile/test/application.yml` - 测试环境

通过设置环境变量 `SPRING_PROFILES_ACTIVE` 来切换环境：
```bash
export SPRING_PROFILES_ACTIVE=prod
```

## 🐛 常见问题

### Redis 连接失败

1. 检查 Redis 是否运行：`redis-cli ping`
2. 检查防火墙规则，确保端口 6379 可访问
3. 检查 Redis 配置中的密码是否正确

### MySQL 连接失败

1. 检查 MySQL 是否运行：`systemctl status mysql`
2. 检查数据库是否创建：`mysql -u root -p -e "SHOW DATABASES;"`
3. 检查表结构是否初始化
4. 检查连接字符串中的时区设置是否正确

### PostgreSQL/pgvector 问题

1. **扩展未安装**: 确保已执行 `CREATE EXTENSION vector;`
2. **权限问题**: 确保数据库用户有创建表的权限
3. **向量维度超限**: 如果使用高维向量，确保 `use-index: false`

## 📞 获取帮助

如果遇到配置问题，请：
1. 查看 [部署文档](./DEPLOYMENT.md)
2. 查看应用日志：`logs/application.log`
3. 提交 [Issue](https://github.com/your-org/ZenoAgent/issues)
