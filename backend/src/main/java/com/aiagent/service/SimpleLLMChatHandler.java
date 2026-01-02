package com.aiagent.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    
    @Autowired
    private ModelManager modelManager;
    
    /**
     * 流式聊天接口
     */
    private interface ChatAssistant {
        TokenStream chat(List<ChatMessage> messages);
    }
    
    /**
     * 创建ChatAssistant实例
     */
    private ChatAssistant createAssistant(StreamingChatModel streamingModel) {
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
            // 使用ModelManager获取模型实例（支持故障转移）
            StreamingChatModel streamingModel = modelManager.getOrCreateStreamingModel(modelId);
            
            // 创建ChatAssistant并调用
            ChatAssistant assistant = createAssistant(streamingModel);
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
        return chat(null, messages);
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
            // 使用ModelManager获取模型实例（支持故障转移）
            ChatModel chatModel = modelManager.getOrCreateChatModel(modelId);
            
            // 直接使用ChatModel的generate方法（LangChain4j 1.9.1的ChatModel接口方法）
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
        return chatNonStreaming(null, messages);
    }
}
