package com.aiagent.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简化的LLM聊天处理器
 * 使用LangChain4j直接调用LLM，不依赖数据库配置
 * 
 * 实现方式：使用AiServices创建接口代理，自动生成TokenStream
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class SimpleLLMChatHandler {
    
    @Value("${aiagent.llm.api-key:}")
    private String defaultApiKey;
    
    @Value("${aiagent.llm.base-url:https://api.openai.com/v1}")
    private String defaultBaseUrl;
    
    @Value("${aiagent.llm.default-model:gpt-4o-mini}")
    private String defaultModelName;
    
    /**
     * 非流式模型缓存（modelName -> OpenAiChatModel）
     */
    private final Map<String, OpenAiChatModel> nonStreamingModelCache = new ConcurrentHashMap<>();
    
    /**
     * 流式聊天接口
     */
    private interface ChatAssistant {
        TokenStream chat(List<ChatMessage> messages);
    }
    
    /**
     * 创建ChatAssistant实例
     */
    private ChatAssistant createAssistant(String apiKey, String baseUrl, String modelName) {
        OpenAiStreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .temperature(0.7)
            .build();
        
        return AiServices.builder(ChatAssistant.class)
                .streamingChatModel(streamingModel)
                .build();
    }
    
    /**
     * 流式聊天（简化版）
     * 
     * 注意：当前实现使用OpenAI，需要配置API Key
     * 未来可以扩展支持更多Provider
     * 
     * @param modelId 模型ID（当前用于选择模型名称）
     * @param messages 消息列表
     * @return TokenStream
     */
    public TokenStream chat(String modelId, List<ChatMessage> messages) {
        log.info("开始LLM流式对话，模型: {}", modelId);
        
        try {
            // 获取API Key
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = defaultApiKey;
            }
            
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("未配置OPENAI_API_KEY，请设置环境变量或配置aiagent.llm.api-key");
            }
            
            // 确定模型名称
            String modelName = defaultModelName;
            if (modelId != null && !modelId.isEmpty() && !modelId.equals(defaultModelName)) {
                // 可以扩展模型ID到模型名称的映射
                modelName = modelId;
            }
            
            // 创建ChatAssistant并调用
            ChatAssistant assistant = createAssistant(apiKey, defaultBaseUrl, modelName);
            return assistant.chat(messages);
                
        } catch (Exception e) {
            log.error("创建LLM模型失败", e);
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 使用默认模型流式聊天
     */
    public TokenStream chatByDefaultModel(List<ChatMessage> messages) {
        return chat(defaultModelName, messages);
    }
    
    /**
     * 非流式聊天（同步调用，返回完整响应）
     * 
     * @param modelId 模型ID
     * @param messages 消息列表
     * @return 完整的AI回复文本
     */
    public String chatNonStreaming(String modelId, List<ChatMessage> messages) {
        log.info("开始LLM非流式对话，模型: {}", modelId);
        
        try {
            // 获取API Key
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = defaultApiKey;
            }
            
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("未配置OPENAI_API_KEY，请设置环境变量或配置aiagent.llm.api-key");
            }
            
            // 确定模型名称
            String modelName = defaultModelName;
            if (modelId != null && !modelId.isEmpty() && !modelId.equals(defaultModelName)) {
                modelName = modelId;
            }
            
            // 获取或创建非流式模型实例
            OpenAiChatModel chatModel = getOrCreateNonStreamingModel(apiKey, modelName);
            
            // 直接使用OpenAiChatModel的generate方法
            // langchain4j 1.9.1: OpenAiChatModel实现了LanguageModel接口
            // generate方法接受List<ChatMessage>，返回Response<AiMessage>
            ChatResponse response = chatModel.chat(messages);
            AiMessage aiMessage = response.aiMessage();
            String responseText = aiMessage != null ? aiMessage.text() : "";
            
            log.debug("LLM非流式对话完成，响应长度: {}", responseText != null ? responseText.length() : 0);
            return responseText;
                
        } catch (Exception e) {
            log.error("LLM非流式调用失败", e);
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 使用默认模型非流式聊天
     */
    public String chatNonStreamingByDefaultModel(List<ChatMessage> messages) {
        return chatNonStreaming(defaultModelName, messages);
    }
    
    /**
     * 获取或创建非流式模型实例
     */
    private OpenAiChatModel getOrCreateNonStreamingModel(String apiKey, String modelName) {
        // 检查缓存
        if (nonStreamingModelCache.containsKey(modelName)) {
            return nonStreamingModelCache.get(modelName);
        }
        
        // 创建新模型实例
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(defaultBaseUrl)
            .modelName(modelName)
            .temperature(0.7)
            .build();
        
        // 缓存模型实例
        nonStreamingModelCache.put(modelName, chatModel);
        log.debug("创建并缓存非流式模型实例: modelName={}", modelName);
        
        return chatModel;
    }
}
