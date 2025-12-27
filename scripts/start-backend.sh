#!/bin/bash

# 启动后端服务脚本

echo "🚀 启动 AI Agent 后端服务..."

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到Java，请先安装Java 17+"
    exit 1
fi

# 检查Maven环境
if ! command -v mvn &> /dev/null; then
    echo "❌ 错误: 未找到Maven，请先安装Maven 3.6+"
    exit 1
fi

# 检查Redis连接
if ! command -v redis-cli &> /dev/null; then
    echo "⚠️  警告: 未找到redis-cli，无法检查Redis连接"
else
    if ! redis-cli ping &> /dev/null; then
        echo "❌ 错误: Redis服务未运行，请先启动Redis"
        echo "   提示: redis-server 或 docker run -d -p 6379:6379 redis:latest"
        exit 1
    else
        echo "✅ Redis服务正常"
    fi
fi

# 检查环境变量
if [ -z "$OPENAI_API_KEY" ]; then
    echo "⚠️  警告: 未设置OPENAI_API_KEY环境变量"
    echo "   提示: export OPENAI_API_KEY=your-api-key"
    read -p "是否继续? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# 切换到后端目录
cd "$(dirname "$0")/../backend" || exit 1

echo "📦 编译项目..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "🎯 启动服务..."
mvn spring-boot:run


