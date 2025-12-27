#!/bin/bash

# API测试脚本

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"

echo "🧪 测试 AI Agent API..."
echo "API地址: $API_BASE_URL"
echo ""

# 测试健康检查
echo "1. 测试健康检查..."
HEALTH_RESPONSE=$(curl -s "$API_BASE_URL/aiagent/health")
if [ $? -eq 0 ]; then
    echo "✅ 健康检查通过"
    echo "   响应: $HEALTH_RESPONSE"
else
    echo "❌ 健康检查失败"
    echo "   请确保后端服务已启动"
    exit 1
fi

echo ""

# 测试执行Agent任务
echo "2. 测试执行Agent任务..."
if [ -z "$OPENAI_API_KEY" ]; then
    echo "⚠️  跳过：未设置OPENAI_API_KEY"
else
    TEST_REQUEST='{
        "content": "你好，请简单介绍一下你自己",
        "modelId": "gpt-4o-mini"
    }'
    
    echo "   发送测试请求..."
    curl -X POST "$API_BASE_URL/aiagent/execute" \
        -H "Content-Type: application/json" \
        -d "$TEST_REQUEST" \
        --max-time 30 \
        -N \
        -s | head -c 200
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ 测试请求发送成功"
        echo "   （注意：这是流式响应，只显示了前200个字符）"
    else
        echo "❌ 测试请求失败"
    fi
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ API测试完成"


