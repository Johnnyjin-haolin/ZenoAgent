#!/bin/bash

# Docker Compose 启动脚本

echo "🐳 使用 Docker Compose 启动 ZenoAgent..."

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "❌ 错误: 未找到 Docker，请先安装 Docker"
    exit 1
fi

# 检查 Docker Compose
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ 错误: 未找到 Docker Compose，请先安装 Docker Compose"
    exit 1
fi

# 检查 .env 文件
if [ ! -f ".env" ]; then
    echo "⚠️  警告: 未找到 .env 文件"
    if [ -f "env.example" ]; then
        echo "📝 从 env.example 创建 .env 文件..."
        cp env.example .env
        echo "✅ 已创建 .env 文件，请编辑后重新运行此脚本"
        exit 1
    else
        echo "❌ 错误: 未找到 env.example 文件"
        exit 1
    fi
fi

# 切换到项目根目录
cd "$(dirname "$0")/.." || exit 1

# 启动服务
echo "🚀 启动服务..."
if command -v docker-compose &> /dev/null; then
    docker-compose up -d
else
    docker compose up -d
fi

if [ $? -eq 0 ]; then
    echo "✅ 服务启动成功！"
    echo ""
    echo "📊 查看服务状态:"
    if command -v docker-compose &> /dev/null; then
        docker-compose ps
    else
        docker compose ps
    fi
    echo ""
    echo "📝 查看日志:"
    echo "   docker-compose logs -f"
    echo ""
    echo "🌐 访问地址:"
    echo "   前端: http://localhost:5173"
    echo "   后端: http://localhost:8080"
else
    echo "❌ 服务启动失败"
    exit 1
fi
