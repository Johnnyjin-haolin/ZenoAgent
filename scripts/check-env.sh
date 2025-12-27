#!/bin/bash

# 环境检查脚本

echo "🔍 检查 AI Agent 项目环境..."
echo ""

ERRORS=0
WARNINGS=0

# 检查Java
echo -n "检查 Java... "
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -ge 17 ]; then
        echo "✅ Java $JAVA_VERSION"
    else
        echo "❌ Java版本过低，需要17+"
        ERRORS=$((ERRORS + 1))
    fi
else
    echo "❌ 未安装Java"
    ERRORS=$((ERRORS + 1))
fi

# 检查Maven
echo -n "检查 Maven... "
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version | head -n 1 | awk '{print $3}')
    echo "✅ Maven $MVN_VERSION"
else
    echo "❌ 未安装Maven"
    ERRORS=$((ERRORS + 1))
fi

# 检查Redis
echo -n "检查 Redis... "
if command -v redis-cli &> /dev/null; then
    if redis-cli ping &> /dev/null; then
        echo "✅ Redis服务运行中"
    else
        echo "⚠️  Redis服务未运行"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo "⚠️  未找到redis-cli（可能未安装Redis）"
    WARNINGS=$((WARNINGS + 1))
fi

# 检查环境变量
echo -n "检查 OPENAI_API_KEY... "
if [ -n "$OPENAI_API_KEY" ]; then
    echo "✅ 已设置"
else
    echo "⚠️  未设置（需要配置OpenAI API Key）"
    WARNINGS=$((WARNINGS + 1))
fi

# 检查后端项目
echo -n "检查后端项目... "
if [ -f "backend/pom.xml" ]; then
    echo "✅ 项目文件存在"
else
    echo "❌ 项目文件不存在"
    ERRORS=$((ERRORS + 1))
fi

# 检查前端项目
echo -n "检查前端项目... "
if [ -d "frontend/src" ]; then
    echo "✅ 项目文件存在"
else
    echo "⚠️  前端项目文件不完整"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo "✅ 环境检查通过！可以开始使用项目了。"
    exit 0
elif [ $ERRORS -eq 0 ]; then
    echo "⚠️  环境检查完成，有 $WARNINGS 个警告"
    echo "   可以继续使用，但建议解决警告项"
    exit 0
else
    echo "❌ 环境检查失败，发现 $ERRORS 个错误，$WARNINGS 个警告"
    echo "   请先解决错误项后再继续"
    exit 1
fi


