package com.aiagent.service;

import com.aiagent.service.llm.ModelManager;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
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
        int messageCount = messages != null ? messages.size() : 0;
        int totalChars = estimateMessageChars(messages);
        log.info("开始LLM非流式对话，模型: {}, messages={}, chars={}", modelId, messageCount, totalChars);
        
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
     * 流式聊天（使用回调处理）
     * 真正的流式实现！使用LangChain4j的StreamingChatResponseHandler
     * 
     * 参考文档: https://docs.langchain4j.dev/tutorials/response-streaming
     * 
     * @param modelId 模型ID
     * @param messages 消息列表
     * @param callback 流式回调
     * @return 完整的AI回复文本（阻塞等待完成）
     */
    public String chatWithCallback(String modelId, List<ChatMessage> messages, StreamingCallback callback) {
        log.info("开始LLM真正流式对话，模型: {}", modelId);
        
        try {
            // 通知开始生成
            if (callback != null) {
                callback.onStart();
            }
            
            // 获取流式模型
            StreamingChatModel streamingModel = modelManager.getOrCreateStreamingModel(modelId);
            
            // 用于收集完整文本和同步
            StringBuilder fullTextBuilder = new StringBuilder();
            final Object lock = new Object();
            final boolean[] completed = {false};
            final Throwable[] error = {null};
            
            // 创建ChatRequest
            ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();
            
            // 创建StreamingChatResponseHandler适配器
            StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    // 收集token
                    fullTextBuilder.append(partialResponse);
                    
                    // 实时发送到callback
                    if (callback != null) {
                        try {
                            callback.onToken(partialResponse);
                        } catch (Exception e) {
                            log.error("回调onToken失败", e);
                        }
                    }
                }
                
                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    String fullText = fullTextBuilder.toString();
                    log.debug("LLM真正流式对话完成，总长度: {}", fullText.length());
                    
                    // 通知完成（这会触发SSE事件发送）
                    if (callback != null) {
                        try {
                            callback.onComplete(fullText);
                        } catch (Exception e) {
                            log.error("回调onComplete失败", e);
                        }
                    }
                    
                    // onCompleteResponse 是在最后一个 onPartialResponse 之后被调用的
                    // 此时所有token都已发送完成，可以安全地唤醒主线程
                    synchronized (lock) {
                        completed[0] = true;
                        lock.notifyAll();
                    }
                }
                
                @Override
                public void onError(Throwable throwable) {
                    synchronized (lock) {
                        error[0] = throwable;
                        completed[0] = true;
                        lock.notifyAll();
                    }
                    
                    log.error("LLM流式对话错误", throwable);
                    
                    // 通知错误
                    if (callback != null) {
                        try {
                            callback.onError(throwable);
                        } catch (Exception e) {
                            log.error("回调onError失败", e);
                        }
                    }
                }
            };
            
            // 调用真正的流式API
            streamingModel.chat(chatRequest, handler);
            
            // 阻塞等待流式处理完成
            synchronized (lock) {
                while (!completed[0]) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("等待LLM响应被中断", e);
                    }
                }
            }
            
            // 检查是否有错误
            if (error[0] != null) {
                throw new RuntimeException("LLM生成失败", error[0]);
            }
            
            return fullTextBuilder.toString();
                
        } catch (Exception e) {
            log.error("LLM真正流式调用失败", e);
            if (callback != null) {
                try {
                    callback.onError(e);
                } catch (Exception ex) {
                    log.error("回调onError失败", ex);
                }
            }
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    private int estimateMessageChars(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ChatMessage message : messages) {
            if (message != null) {
                total += message.toString().length();
            }
        }
        return total;
    }
}
