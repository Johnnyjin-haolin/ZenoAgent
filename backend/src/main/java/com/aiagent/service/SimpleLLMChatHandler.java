package com.aiagent.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

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
}
