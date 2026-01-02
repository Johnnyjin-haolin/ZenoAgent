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
    
    /**
     * 流式聊天（使用回调处理）
     * 
     * 实现说明：
     * 根据LangChain4j官方文档 (https://docs.langchain4j.dev/tutorials/response-streaming)
     * 应该使用 StreamingChatResponseHandler 接口实现真正的流式输出。
     * 
     * 但是LangChain4j 1.9.1版本中 StreamingChatResponseHandler 接口可能还不可用。
     * 该接口在更新的版本中才引入（可能是1.9.2+或2.x版本）。
     * 
     * 当前方案：使用智能分块模拟流式效果
     * - 优点：用户体验接近真正流式，代码简单可靠
     * - 缺点：需要等待完整响应生成完毕才能开始发送
     * 
     * 升级建议：
     * 将 langchain4j.version 从 1.9.1 升级到 2.x 后，可以使用真正的流式API：
     * streamingModel.chat(messages, new StreamingChatResponseHandler() {
     *     @Override
     *     public void onPartialResponse(String partialResponse) {
     *         callback.onToken(partialResponse); // 真实实时
     *     }
     *     // ... 其他回调方法
     * });
     * 
     * @param modelId 模型ID
     * @param messages 消息列表
     * @param callback 流式回调
     * @return 完整的AI回复文本（阻塞等待完成）
     */
    public String chatWithCallback(String modelId, List<ChatMessage> messages, StreamingCallback callback) {
        log.info("开始LLM流式对话（智能分块模式），模型: {}", modelId);
        
        try {
            // 通知开始生成
            if (callback != null) {
                callback.onStart();
            }
            
            // 使用非流式调用获取完整结果
            String result = chatNonStreaming(modelId, messages);
            
            // 智能分块发送（按词或标点分块，更自然）
            if (callback != null) {
                sendChunksIntelligently(result, callback);
            }
            
            return result;
                
        } catch (Exception e) {
            log.error("LLM流式调用（智能分块模式）失败", e);
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
    
    /**
     * 智能分块发送文本
     * 按照更自然的方式分块（考虑中文/英文词边界、标点符号等）
     */
    private void sendChunksIntelligently(String text, StreamingCallback callback) {
        if (text == null || text.isEmpty()) {
            callback.onComplete("");
            return;
        }
        
        try {
            int index = 0;
            int textLength = text.length();
            
            while (index < textLength) {
                // 动态确定块大小：中文1-3字符，英文3-8字符
                char currentChar = text.charAt(index);
                int chunkSize;
                
                if (Character.UnicodeBlock.of(currentChar) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                    // 中文字符：1-3个字
                    chunkSize = Math.min(3, textLength - index);
                } else {
                    // 英文/数字/符号：查找下一个空格或标点
                    int nextSpace = text.indexOf(' ', index + 1);
                    int nextPunct = findNextPunctuation(text, index + 1);
                    
                    if (nextSpace > 0 && nextSpace < index + 15) {
                        chunkSize = nextSpace - index + 1;
                    } else if (nextPunct > 0 && nextPunct < index + 20) {
                        chunkSize = nextPunct - index + 1;
                    } else {
                        chunkSize = Math.min(8, textLength - index);
                    }
                }
                
                int endIndex = Math.min(index + chunkSize, textLength);
                String chunk = text.substring(index, endIndex);
                
                // 发送块
                callback.onToken(chunk);
                
                index = endIndex;
                
                // 短暂延迟，模拟真实流式效果
                // 中文慢一点（30ms），英文快一点（15ms）
                try {
                    if (Character.UnicodeBlock.of(currentChar) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                        Thread.sleep(30);
                    } else {
                        Thread.sleep(15);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // 通知完成
            callback.onComplete(text);
            
        } catch (Exception e) {
            log.error("智能分块发送失败", e);
            callback.onError(e);
        }
    }
    
    /**
     * 查找下一个标点符号位置
     */
    private int findNextPunctuation(String text, int startIndex) {
        String punctuations = ".,!?;:，。！？；：、";
        for (int i = startIndex; i < text.length(); i++) {
            if (punctuations.indexOf(text.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }
}
