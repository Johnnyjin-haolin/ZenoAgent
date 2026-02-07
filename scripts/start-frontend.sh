#!/bin/bash

# 启动前端服务脚本

echo "🚀 启动 AI Agent 前端服务..."

# 检查 Node.js 环境
if ! command -v node &> /dev/null; then
    echo "❌ 错误: 未找到 Node.js，请先安装 Node.js 20+"
    exit 1
fi

# 检查 pnpm 环境
if ! command -v pnpm &> /dev/null; then
    echo "⚠️  警告: 未找到 pnpm，尝试使用 npm 安装 pnpm..."
    npm install -g pnpm
    if [ $? -ne 0 ]; then
        echo "❌ 错误: 无法安装 pnpm，请手动安装: npm install -g pnpm"
        exit 1
    fi
fi

# 切换到前端目录
cd "$(dirname "$0")/../frontend" || exit 1

# 检查 node_modules
if [ ! -d "node_modules" ]; then
    echo "📦 安装依赖..."
    pnpm install
    if [ $? -ne 0 ]; then
        echo "❌ 依赖安装失败"
        exit 1
    fi
fi

# 检查后端服务
if [ -z "$VITE_API_BASE_URL" ]; then
    echo "⚠️  警告: 未设置 VITE_API_BASE_URL 环境变量"
    echo "   提示: export VITE_API_BASE_URL=http://localhost:8080"
fi

echo "🎯 启动开发服务器..."
pnpm dev
