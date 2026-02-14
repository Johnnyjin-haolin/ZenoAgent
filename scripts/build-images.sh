#!/bin/bash

# ZenoAgent Docker 镜像构建脚本
# 用于构建和发布 Docker 镜像到 Docker Hub 或其他镜像仓库

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置变量
REGISTRY="${DOCKER_REGISTRY:-docker.io}"  # 默认使用 Docker Hub
NAMESPACE="${DOCKER_NAMESPACE:-zenoagent}"  # 你的 Docker Hub 用户名或组织名
VERSION="${VERSION:-latest}"  # 版本标签，默认 latest
BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

# 镜像名称
BACKEND_IMAGE="${REGISTRY}/${NAMESPACE}/zenoagent-backend"
FRONTEND_IMAGE="${REGISTRY}/${NAMESPACE}/zenoagent-frontend"

# 函数：打印信息
info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 函数：检查 Docker 是否安装
check_docker() {
    if ! command -v docker &> /dev/null; then
        error "Docker 未安装，请先安装 Docker"
        exit 1
    fi
    info "Docker 已安装: $(docker --version)"
}

# 函数：检查是否登录 Docker Hub
check_login() {
    if [ "$1" = "push" ]; then
        if ! docker info | grep -q "Username"; then
            warn "未检测到 Docker 登录信息"
            info "请先登录 Docker Hub: docker login"
            read -p "是否现在登录? (y/n) " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                docker login
            else
                error "需要登录才能推送镜像"
                exit 1
            fi
        fi
    fi
}

# 函数：构建后端镜像
build_backend() {
    info "开始构建后端镜像..."
    cd backend
    
    docker build \
        --tag "${BACKEND_IMAGE}:${VERSION}" \
        --tag "${BACKEND_IMAGE}:latest" \
        --build-arg BUILD_DATE="${BUILD_DATE}" \
        --build-arg GIT_COMMIT="${GIT_COMMIT}" \
        --build-arg VERSION="${VERSION}" \
        .
    
    info "后端镜像构建完成: ${BACKEND_IMAGE}:${VERSION}"
    cd ..
}

# 函数：构建前端镜像
build_frontend() {
    info "开始构建前端镜像..."
    cd frontend
    
    # 从环境变量或参数获取 API URL
    VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://localhost:8080}"
    
    docker build \
        --tag "${FRONTEND_IMAGE}:${VERSION}" \
        --tag "${FRONTEND_IMAGE}:latest" \
        --build-arg VITE_API_BASE_URL="${VITE_API_BASE_URL}" \
        --build-arg BUILD_DATE="${BUILD_DATE}" \
        --build-arg GIT_COMMIT="${GIT_COMMIT}" \
        --build-arg VERSION="${VERSION}" \
        .
    
    info "前端镜像构建完成: ${FRONTEND_IMAGE}:${VERSION}"
    cd ..
}

# 函数：推送镜像
push_images() {
    info "开始推送镜像到 ${REGISTRY}..."
    
    docker push "${BACKEND_IMAGE}:${VERSION}"
    docker push "${BACKEND_IMAGE}:latest"
    info "后端镜像推送完成"
    
    docker push "${FRONTEND_IMAGE}:${VERSION}"
    docker push "${FRONTEND_IMAGE}:latest"
    info "前端镜像推送完成"
    
    info "所有镜像已推送到 ${REGISTRY}/${NAMESPACE}"
}

# 函数：显示镜像信息
show_images() {
    info "已构建的镜像:"
    docker images | grep -E "${NAMESPACE}|REPOSITORY" || true
}

# 函数：显示使用帮助
show_help() {
    cat << EOF
ZenoAgent Docker 镜像构建脚本

用法:
    $0 [命令] [选项]

命令:
    build           构建所有镜像
    build-backend   仅构建后端镜像
    build-frontend  仅构建前端镜像
    push            推送镜像到镜像仓库
    build-push      构建并推送镜像
    list            列出已构建的镜像
    help            显示此帮助信息

选项:
    -r, --registry REGISTRY     镜像仓库地址 (默认: docker.io)
    -n, --namespace NAMESPACE   命名空间/用户名 (默认: zenoagent)
    -v, --version VERSION       版本标签 (默认: latest)
    -u, --api-url URL          前端 API 基础 URL (默认: http://localhost:8080)

环境变量:
    DOCKER_REGISTRY     镜像仓库地址
    DOCKER_NAMESPACE    命名空间/用户名
    VERSION             版本标签
    VITE_API_BASE_URL   前端 API 基础 URL

示例:
    # 构建所有镜像
    $0 build

    # 构建并推送到 Docker Hub
    $0 build-push -n your-username -v 1.0.0

    # 构建并推送到私有仓库
    $0 build-push -r registry.example.com -n myorg -v 1.0.0

    # 仅构建后端镜像
    $0 build-backend -v 1.0.0

EOF
}

# 解析命令行参数
COMMAND=""
while [[ $# -gt 0 ]]; do
    case $1 in
        build|build-backend|build-frontend|push|build-push|list|help)
            COMMAND="$1"
            shift
            ;;
        -r|--registry)
            REGISTRY="$2"
            shift 2
            ;;
        -n|--namespace)
            NAMESPACE="$2"
            shift 2
            ;;
        -v|--version)
            VERSION="$2"
            shift 2
            ;;
        -u|--api-url)
            VITE_API_BASE_URL="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            error "未知参数: $1"
            show_help
            exit 1
            ;;
    esac
done

# 如果没有指定命令，显示帮助
if [ -z "$COMMAND" ]; then
    show_help
    exit 0
fi

# 更新镜像名称
BACKEND_IMAGE="${REGISTRY}/${NAMESPACE}/zenoagent-backend"
FRONTEND_IMAGE="${REGISTRY}/${NAMESPACE}/zenoagent-frontend"

# 执行命令
case $COMMAND in
    build)
        check_docker
        build_backend
        build_frontend
        show_images
        ;;
    build-backend)
        check_docker
        build_backend
        show_images
        ;;
    build-frontend)
        check_docker
        build_frontend
        show_images
        ;;
    push)
        check_docker
        check_login push
        push_images
        ;;
    build-push)
        check_docker
        check_login push
        build_backend
        build_frontend
        push_images
        show_images
        ;;
    list)
        show_images
        ;;
    help)
        show_help
        ;;
    *)
        error "未知命令: $COMMAND"
        show_help
        exit 1
        ;;
esac

info "完成！"
