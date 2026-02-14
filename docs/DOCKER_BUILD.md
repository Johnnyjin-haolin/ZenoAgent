# Docker 镜像构建和发布指南

本文档介绍如何为 ZenoAgent 项目构建和发布 Docker 镜像。

## 📋 目录

- [快速开始](#快速开始)
- [手动构建](#手动构建)
- [使用构建脚本](#使用构建脚本)
- [发布到 Docker Hub](#发布到-docker-hub)
- [发布到其他镜像仓库](#发布到其他镜像仓库)
- [CI/CD 自动化](#cicd-自动化)
- [镜像标签策略](#镜像标签策略)
- [最佳实践](#最佳实践)

## 🚀 快速开始

### 前置要求

- Docker 20.10+
- Docker Compose 2.0+（可选）
- Git

### 使用预构建镜像（推荐）

如果你使用 Docker Compose，可以直接使用预构建的镜像：

```yaml
services:
  backend:
    image: zenoagent/zenoagent-backend:latest
    # ...
  frontend:
    image: zenoagent/zenoagent-frontend:latest
    # ...
```

## 🔨 手动构建

### 构建后端镜像

```bash
cd backend
docker build -t zenoagent-backend:latest .
```

### 构建前端镜像

```bash
cd frontend
docker build -t zenoagent-frontend:latest \
  --build-arg VITE_API_BASE_URL=http://localhost:8080 .
```

### 使用 Docker Compose 构建

```bash
# 构建所有服务
docker-compose build

# 构建特定服务
docker-compose build backend
docker-compose build frontend
```

## 📜 使用构建脚本

项目提供了自动化构建脚本，位于 `scripts/build-images.sh`。

### 基本用法

```bash
# 给脚本添加执行权限
chmod +x scripts/build-images.sh

# 构建所有镜像
./scripts/build-images.sh build

# 构建并推送到 Docker Hub
./scripts/build-images.sh build-push -n your-username -v 1.0.0
```

### 脚本命令

- `build` - 构建所有镜像
- `build-backend` - 仅构建后端镜像
- `build-frontend` - 仅构建前端镜像
- `push` - 推送镜像到镜像仓库
- `build-push` - 构建并推送镜像
- `list` - 列出已构建的镜像

### 脚本选项

```bash
-r, --registry REGISTRY      # 镜像仓库地址 (默认: docker.io)
-n, --namespace NAMESPACE   # 命名空间/用户名 (默认: zenoagent)
-v, --version VERSION       # 版本标签 (默认: latest)
-u, --api-url URL          # 前端 API 基础 URL
```

### 示例

```bash
# 构建开发版本
./scripts/build-images.sh build -v dev

# 构建并推送到 Docker Hub
./scripts/build-images.sh build-push \
  -n your-dockerhub-username \
  -v 1.0.0

# 构建并推送到私有仓库
./scripts/build-images.sh build-push \
  -r registry.example.com \
  -n myorg \
  -v 1.0.0

# 构建前端镜像，指定 API URL
./scripts/build-images.sh build-frontend \
  -u https://api.example.com \
  -v 1.0.0
```

## 🐳 发布到 Docker Hub

### 1. 登录 Docker Hub

```bash
docker login
# 输入你的 Docker Hub 用户名和密码
```

### 2. 构建并标记镜像

```bash
# 构建后端镜像
docker build -t your-username/zenoagent-backend:1.0.0 ./backend
docker tag your-username/zenoagent-backend:1.0.0 your-username/zenoagent-backend:latest

# 构建前端镜像
docker build -t your-username/zenoagent-frontend:1.0.0 \
  --build-arg VITE_API_BASE_URL=http://localhost:8080 \
  ./frontend
docker tag your-username/zenoagent-frontend:1.0.0 your-username/zenoagent-frontend:latest
```

### 3. 推送镜像

```bash
# 推送后端镜像
docker push your-username/zenoagent-backend:1.0.0
docker push your-username/zenoagent-backend:latest

# 推送前端镜像
docker push your-username/zenoagent-frontend:1.0.0
docker push your-username/zenoagent-frontend:latest
```

### 4. 使用构建脚本（推荐）

```bash
./scripts/build-images.sh build-push \
  -n your-username \
  -v 1.0.0
```

## 🌐 发布到其他镜像仓库

### GitHub Container Registry (ghcr.io)

```bash
# 登录 GitHub Container Registry
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin

# 构建并推送
./scripts/build-images.sh build-push \
  -r ghcr.io \
  -n your-github-username \
  -v 1.0.0
```

### 阿里云容器镜像服务

```bash
# 登录
docker login --username=your-username registry.cn-hangzhou.aliyuncs.com

# 构建并推送
./scripts/build-images.sh build-push \
  -r registry.cn-hangzhou.aliyuncs.com \
  -n your-namespace \
  -v 1.0.0
```

### 私有镜像仓库

```bash
# 登录私有仓库
docker login registry.example.com

# 构建并推送
./scripts/build-images.sh build-push \
  -r registry.example.com \
  -n your-org \
  -v 1.0.0
```

## 🤖 CI/CD 自动化

### GitHub Actions 示例

创建 `.github/workflows/docker-build.yml`:

```yaml
name: Build and Push Docker Images

on:
  push:
    tags:
      - 'v*'
    branches:
      - main
  pull_request:
    branches:
      - main

env:
  REGISTRY: ghcr.io
  NAMESPACE: ${{ github.repository_owner }}

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: |
            ${{ env.REGISTRY }}/${{ env.NAMESPACE }}/zenoagent-backend
            ${{ env.REGISTRY }}/${{ env.NAMESPACE }}/zenoagent-frontend
          tags: |
            type=ref,event=branch
            type=ref,event=pr
            type=semver,pattern={{version}}
            type=semver,pattern={{major}}.{{minor}}
            type=sha

      - name: Build and push backend
        uses: docker/build-push-action@v5
        with:
          context: ./backend
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Build and push frontend
        uses: docker/build-push-action@v5
        with:
          context: ./frontend
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          build-args: |
            VITE_API_BASE_URL=http://localhost:8080
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### GitLab CI 示例

创建 `.gitlab-ci.yml`:

```yaml
stages:
  - build
  - push

variables:
  DOCKER_DRIVER: overlay2
  DOCKER_TLS_CERTDIR: "/certs"

build-backend:
  stage: build
  script:
    - docker build -t $CI_REGISTRY_IMAGE/zenoagent-backend:$CI_COMMIT_TAG ./backend
    - docker push $CI_REGISTRY_IMAGE/zenoagent-backend:$CI_COMMIT_TAG
  only:
    - tags

build-frontend:
  stage: build
  script:
    - docker build -t $CI_REGISTRY_IMAGE/zenoagent-frontend:$CI_COMMIT_TAG \
        --build-arg VITE_API_BASE_URL=http://localhost:8080 \
        ./frontend
    - docker push $CI_REGISTRY_IMAGE/zenoagent-frontend:$CI_COMMIT_TAG
  only:
    - tags
```

## 🏷️ 镜像标签策略

推荐使用以下标签策略：

- `latest` - 最新稳定版本
- `1.0.0` - 语义化版本号
- `1.0` - 主版本.次版本
- `v1.0.0` - 带 v 前缀的版本号
- `dev` - 开发版本
- `sha-<commit-hash>` - Git 提交哈希

示例：

```bash
# 构建多个标签
docker build -t zenoagent/zenoagent-backend:1.0.0 \
             -t zenoagent/zenoagent-backend:1.0 \
             -t zenoagent/zenoagent-backend:latest \
             ./backend
```

## ✅ 最佳实践

### 1. 使用多阶段构建

项目已经使用了多阶段构建，可以减小最终镜像大小。

### 2. 利用构建缓存

```bash
# Docker 会自动缓存层，按依赖顺序复制文件可以优化缓存
# 先复制依赖文件（package.json, pom.xml）
# 再复制源代码
```

### 3. 使用 .dockerignore

项目已包含 `.dockerignore` 文件，排除不必要的文件：

- `node_modules/`
- `target/`
- `.git/`
- `.env` 文件

### 4. 安全扫描

```bash
# 使用 Trivy 扫描镜像漏洞
trivy image zenoagent/zenoagent-backend:latest

# 使用 Docker Scout
docker scout cves zenoagent/zenoagent-backend:latest
```

### 5. 镜像大小优化

- ✅ 使用 Alpine 基础镜像
- ✅ 多阶段构建
- ✅ 清理构建缓存
- ✅ 合并 RUN 命令

### 6. 版本管理

```bash
# 使用 Git 标签管理版本
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0

# 构建对应版本的镜像
./scripts/build-images.sh build-push -v 1.0.0
```

### 7. 测试镜像

```bash
# 运行容器测试
docker run -d -p 8080:8080 zenoagent/zenoagent-backend:latest
curl http://localhost:8080/actuator/health

# 使用 docker-compose 测试
docker-compose up -d
```

## 📝 发布清单

发布新版本前，请确认：

- [ ] 更新版本号
- [ ] 更新 CHANGELOG.md
- [ ] 构建并测试镜像
- [ ] 扫描镜像安全漏洞
- [ ] 推送到镜像仓库
- [ ] 更新文档中的镜像版本
- [ ] 创建 Git 标签

## 🔗 相关资源

- [Docker 官方文档](https://docs.docker.com/)
- [Docker Hub](https://hub.docker.com/)
- [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [部署指南](../DEPLOYMENT.md)

## ❓ 常见问题

### Q: 构建镜像时遇到网络问题？

A: 使用国内镜像源或代理：

```bash
# 配置 Docker 镜像加速
# 编辑 /etc/docker/daemon.json
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn"
  ]
}
```

### Q: 如何查看镜像大小？

```bash
docker images zenoagent/zenoagent-backend
```

### Q: 如何删除未使用的镜像？

```bash
# 删除悬空镜像
docker image prune

# 删除所有未使用的镜像
docker image prune -a
```

### Q: 如何查看镜像构建历史？

```bash
docker history zenoagent/zenoagent-backend:latest
```
