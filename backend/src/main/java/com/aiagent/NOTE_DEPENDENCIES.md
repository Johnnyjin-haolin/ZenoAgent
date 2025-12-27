# 外部依赖说明

## ⚠️ 重要提示

本项目是一个轻量化的独立项目，但为了保留RAG和MCP的核心能力，以下功能需要外部依赖：

## 1. RAG功能依赖

### 当前状态
- `RAGEnhancer.java` 已创建，但为占位实现
- 实际RAG检索功能需要集成

### 需要集成的模块
- **EmbeddingHandler** - 向量检索处理器
- **PgVector数据库** - 向量存储
- **知识库管理模块** - 知识库文档管理

### 可选方案

**方案1：集成原项目RAG模块**
```xml
<dependency>
    <groupId>org.jeecgframework.boot</groupId>
    <artifactId>jeecg-boot-module-airag</artifactId>
    <version>3.8.1</version>
</dependency>
```

然后在`RAGEnhancer.java`中注入`EmbeddingHandler`。

**方案2：简化实现**
- 使用简单的关键词匹配
- 或集成其他向量数据库SDK

**方案3：RAG功能可选**
- 如果不需要RAG功能，可以完全移除
- 项目仍可正常使用（仅支持简单对话和工具调用）

## 2. MCP工具功能依赖

### 当前状态
- `ToolOrchestrator.java` 已创建，但为占位实现
- 实际工具调用功能需要集成

### 需要集成的模块
- **McpToolRegistry** - MCP工具注册表
- **McpToolInvoker** - MCP工具调用器
- **MCP工具定义** - 具体的工具实现

### 可选方案

**方案1：集成原项目MCP模块**
```xml
<dependency>
    <groupId>org.jeecgframework.boot</groupId>
    <artifactId>jeecg-module-mcp</artifactId>
    <version>3.8.1</version>
</dependency>
```

然后在`ToolOrchestrator.java`中注入相关组件。

**方案2：实现简化的工具系统**
- 创建自己的工具注册和调用机制
- 不依赖MCP协议

**方案3：工具功能可选**
- 如果不需要工具调用功能，可以完全移除
- 项目仍可正常使用（仅支持简单对话和RAG）

## 3. LLM模型管理

### 当前状态
- 模型配置通过`application.yml`管理
- 需要LangChain4j支持

### 已包含依赖
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.35.0</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.35.0</version>
</dependency>
```

### 需要配置
- API Key通过环境变量或配置文件设置
- 支持的Provider：OpenAI, DeepSeek等（LangChain4j支持的）

## 4. 建议的实现路径

### 阶段1：最小可用版本（当前）
- ✅ 基础框架
- ✅ Redis存储
- ✅ 简单对话功能
- ⏳ RAG功能（占位）
- ⏳ MCP工具（占位）

### 阶段2：完整功能版本
- 集成RAG模块
- 集成MCP模块
- 完善配置管理

### 阶段3：优化版本
- 性能优化
- 监控和日志
- 扩展功能

## 5. 快速开始（最小版本）

即使不集成RAG和MCP，项目也可以运行：

1. **仅支持简单对话**
   - 直接LLM对话
   - 无需RAG和MCP

2. **配置LLM API Key**
   ```yaml
   aiagent:
     model:
       default-model-id: "gpt-4o-mini"
   ```

3. **启动项目**
   ```bash
   mvn spring-boot:run
   ```

4. **测试API**
   ```bash
   curl -X POST http://localhost:8080/aiagent/execute \
     -H "Content-Type: application/json" \
     -d '{"content": "你好"}'
   ```

## 6. 集成指南

### 集成RAG功能

1. 添加依赖（如方案1）
2. 修改`RAGEnhancer.java`，注入`EmbeddingHandler`
3. 配置PgVector数据库连接
4. 测试RAG检索功能

### 集成MCP功能

1. 添加依赖（如方案1）
2. 修改`ToolOrchestrator.java`，注入`McpToolRegistry`和`McpToolInvoker`
3. 配置MCP工具
4. 测试工具调用功能

## 总结

- **核心框架**：已完成，可独立运行
- **RAG功能**：需要集成（或使用简化版本）
- **MCP功能**：需要集成（或使用简化版本）
- **LLM功能**：已支持（通过LangChain4j）

项目可以按需选择功能模块，保持轻量化的特点。


