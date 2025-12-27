# LLM调用实现说明

## 当前状态

`SimpleLLMChatHandler` 是一个简化实现，使用了LangChain4j的TokenStreamBuilder。

**注意**：LangChain4j的API可能在不同版本中有所不同，如果TokenStreamBuilder不存在，需要使用其他方式创建TokenStream。

## 实现选项

### 选项1：使用StreamingChatLanguageModel

```java
StreamingChatLanguageModel streamingModel = OpenAiStreamingChatModel.builder()
    .apiKey(apiKey)
    .baseUrl(baseUrl)
    .modelName(modelName)
    .temperature(0.7)
    .build();

// 创建TokenStream的方式取决于LangChain4j版本
// 方式1：使用TokenStreamBuilder（如果存在）
TokenStream tokenStream = TokenStreamBuilder.builder(streamingModel)
    .messages(messages)
    .build();

// 方式2：直接使用StreamingChatLanguageModel的流式方法
// 需要手动处理流式响应
```

### 选项2：集成原项目的LLMHandler

如果原项目的`LLMHandler`已经实现了完整的TokenStream创建逻辑，可以直接集成：

1. 复制`LLMHandler`相关代码到新项目
2. 创建简化的模型配置（不使用数据库）
3. 使用配置类或环境变量配置模型参数

### 选项3：使用AiServices（LangChain4j推荐方式）

```java
interface ChatAssistant {
    TokenStream chat(List<ChatMessage> messages);
}

ChatAssistant assistant = AiServices.builder(ChatAssistant.class)
    .streamingChatLanguageModel(streamingModel)
    .build();

TokenStream tokenStream = assistant.chat(messages);
```

## 推荐方案

### 短期方案（占位实现）

当前`SimpleLLMChatHandler`使用占位实现，支持基本功能。如果TokenStreamBuilder不存在，可以：

1. 使用LangChain4j的`AiServices`创建接口代理
2. 或者创建一个简化的流式包装器

### 长期方案（完整实现）

1. 集成原项目的LLMHandler逻辑
2. 支持多个LLM Provider（OpenAI、Azure OpenAI、Anthropic等）
3. 支持模型配置（从配置文件或环境变量读取）
4. 支持参数动态调整（temperature、top_p等）

## 配置说明

在`application.yml`中配置：

```yaml
aiagent:
  llm:
    api-key: ${OPENAI_API_KEY:}  # 优先使用环境变量
    base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
    default-model: ${DEFAULT_MODEL:gpt-4o-mini}
```

## 后续改进

1. 支持多Provider（OpenAI、Azure、Anthropic等）
2. 支持从配置文件加载模型配置
3. 支持流式和非流式两种模式
4. 添加重试机制和错误处理
5. 支持token统计和限流

## 参考文档

- LangChain4j官方文档：https://github.com/langchain4j/langchain4j
- 原项目实现：`jeecg-boot/jeecg-boot-module/jeecg-boot-module-airag/src/main/java/org/jeecg/modules/airag/llm/handler/`


