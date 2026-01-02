package com.aiagent.service.impl;

import com.aiagent.constant.AgentConstants;
import com.aiagent.service.*;
import com.aiagent.storage.ConversationStorage;
import com.aiagent.util.LocalCache;
import com.aiagent.util.StringUtils;
import com.aiagent.util.UUIDGenerator;
import com.aiagent.vo.*;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI Agent 服务实现（ReAct架构版本）
 * 
 * 使用ReAct循环实现自主思考和决策能力
 * 
 * @author aiagent
 */
@Slf4j
@Service
public class AgentServiceImpl implements IAgentService {
    
    @Autowired
    private ReActEngine reActEngine;
    
    @Autowired
    private MemorySystem memorySystem;
    
    @Autowired
    private ConversationStorage conversationStorage;
    
    @Autowired
    private AgentStateMachine stateMachine;
    
    @Override
    public SseEmitter execute(AgentRequest request) {
        log.info("开始执行Agent任务（ReAct架构）: {}", request.getContent());
        
        // 1. 创建SSE连接
        SseEmitter emitter = new SseEmitter(-1L);
        String requestId = UUIDGenerator.generate();
        
        // 2. 错误处理
        emitter.onError(throwable -> {
            log.error("SSE连接错误: {}", throwable.getMessage());
            LocalCache.remove(AgentConstants.CACHE_PREFIX_AGENT_SSE, requestId);
            try {
                emitter.complete();
            } catch (Exception ignore) {}
        });
        
        // 3. 发送初始事件
        sendEvent(emitter, AgentEventData.builder()
            .requestId(requestId)
            .event(AgentConstants.EVENT_AGENT_START)
            .message("Agent 开始执行任务（ReAct模式）")
            .conversationId(request.getConversationId())
            .build());
        
        // 4. 缓存emitter
        LocalCache.put(AgentConstants.CACHE_PREFIX_AGENT_SSE, requestId, emitter);
        
        // 5. 异步执行任务
        CompletableFuture.runAsync(() -> {
            try {
                executeWithReAct(request, requestId, emitter);
            } catch (Exception e) {
                log.error("Agent任务执行失败", e);
                sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_ERROR)
                    .message("执行失败: " + e.getMessage())
                    .conversationId(request.getConversationId())
                    .build());
                closeSSE(emitter, requestId);
            }
        });
        
        return emitter;
    }
    
    /**
     * 使用ReAct循环执行任务
     */
    private void executeWithReAct(AgentRequest request, String requestId, SseEmitter emitter) {
        // 1. 加载或创建上下文
        AgentContext context = loadOrCreateContext(request);
        
        // 1.1 如果没有conversationId，创建新对话
        if (StringUtils.isEmpty(request.getConversationId())) {
            try {
                String conversationId = context.getConversationId();
                
                // 使用ConversationInfo对象替代Map
                ConversationInfo conversationInfo = ConversationInfo.builder()
                    .id(conversationId)
                    .title(generateTitle(request.getContent()))
                    .status("active")
                    .messageCount(0)
                    .build();
                
                conversationStorage.saveConversation(conversationInfo);
                request.setConversationId(conversationId);
                
                log.info("创建新对话: conversationId={}", conversationId);
            } catch (Exception e) {
                log.error("创建对话失败", e);
            }
        }
        
        // 2. 保存用户消息到记忆
        UserMessage userMessage = new UserMessage(request.getContent());
        memorySystem.saveShortTermMemory(context.getConversationId(), userMessage);
        if (context.getMessages() == null) {
            context.setMessages(new java.util.ArrayList<>());
        }
        context.getMessages().add(userMessage);
        
        // 3. 设置上下文变量（使用具体属性）
        context.setModelId(StringUtils.getString(request.getModelId(), "gpt-4o-mini"));
        context.setEnabledMcpGroups(request.getEnabledMcpGroups());
        context.setKnowledgeIds(request.getKnowledgeIds());
        context.setRequestId(requestId);
        
        // 3.1 设置流式输出回调（使用CountDownLatch确保流式完成后再关闭SSE）
        java.util.concurrent.CountDownLatch streamingCompleteLatch = new java.util.concurrent.CountDownLatch(1);
        context.setStreamingCallback(new com.aiagent.service.StreamingCallback() {
            @Override
            public void onToken(String token) {
                // 实时发送token到前端
                sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_MESSAGE)
                    .content(token)
                    .conversationId(context.getConversationId())
                    .build());
            }
            
            @Override
            public void onComplete(String fullText) {
                log.debug("LLM生成完成，文本长度: {}", fullText.length());
                // 发送流式完成事件，通知前端所有token都已发送
                sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_STREAM_COMPLETE)
                    .message("流式输出完成")
                    .conversationId(context.getConversationId())
                    .build());
                // 释放锁，允许主线程继续执行并关闭SSE
                streamingCompleteLatch.countDown();
            }
            
            @Override
            public void onError(Throwable error) {
                log.error("LLM流式输出错误", error);
                sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_ERROR)
                    .message("生成失败: " + error.getMessage())
                    .conversationId(context.getConversationId())
                    .build());
                // 发生错误也要释放锁
                streamingCompleteLatch.countDown();
            }
            
            @Override
            public void onStart() {
                log.debug("LLM开始生成");
                // 可以发送一个开始生成的事件
                sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_THINKING)
                    .message("开始生成回复...")
                    .conversationId(context.getConversationId())
                    .build());
            }
        });
        
        // 4. 注册状态变更监听器
        stateMachine.initialize(context, newState -> {
            sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_THINKING)
                .message("状态: " + newState.getDescription())
                .conversationId(context.getConversationId())
                .build());
        });
        
        // 5. 执行ReAct循环
        sendEvent(emitter, AgentEventData.builder()
            .requestId(requestId)
            .event(AgentConstants.EVENT_AGENT_THINKING)
            .message("开始ReAct循环...")
            .conversationId(context.getConversationId())
            .build());
        
        ActionResult finalResult = reActEngine.execute(request.getContent(), context);
        
        // 6. 处理最终结果
        if (finalResult.isSuccess()) {
            // 保存AI回复到记忆
            dev.langchain4j.data.message.AiMessage aiMessage = 
                new dev.langchain4j.data.message.AiMessage(finalResult.getData().toString());
            context.getMessages().add(aiMessage);
            memorySystem.saveShortTermMemory(context.getConversationId(), aiMessage);
            
            // 更新对话消息数量
            conversationStorage.incrementMessageCount(context.getConversationId());
            
            // 注意：如果是LLM_GENERATE且使用了流式输出，内容已经通过callback发送了
            // 这里不再重复发送完整内容，只在metadata中标记streaming为false的情况下才发送
            Object metadata = finalResult.getMetadata();
            boolean isStreaming = false;
            if (metadata instanceof Map) {
                Map<?, ?> metaMap = (Map<?, ?>) metadata;
                isStreaming = Boolean.TRUE.equals(metaMap.get("streaming"));
            }
            
            if (!isStreaming) {
                // 非流式结果（如工具调用结果），需要发送完整内容
                sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_MESSAGE)
                    .content(finalResult.getData().toString())
                    .conversationId(context.getConversationId())
                    .build());
            } else {
                log.debug("流式输出已完成，等待所有SSE事件发送完成...");
                // 如果是流式输出，等待流式完成后再继续
                // 这样可以确保所有token都通过SSE发送完毕，避免关闭SSE时还有残留事件
                try {
                    // 等待最多30秒（正常情况下应该很快完成）
                    boolean completed = streamingCompleteLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
                    if (completed) {
                        log.debug("流式输出已完全结束，可以安全关闭SSE");
                    } else {
                        log.warn("等待流式输出完成超时（30秒），强制继续");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("等待流式输出完成被中断", e);
                }
            }
        } else {
            sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_ERROR)
                .message("执行失败: " + finalResult.getError())
                .conversationId(context.getConversationId())
                .build());
        }
        
        // 7. 保存上下文
        memorySystem.saveContext(context);
        
        // 8. 关闭SSE（此时所有流式事件都已发送完成）
        closeSSE(emitter, requestId);
    }
    
    /**
     * 加载或创建上下文
     */
    private AgentContext loadOrCreateContext(AgentRequest request) {
        String conversationId = request.getConversationId();
        if (StringUtils.isEmpty(conversationId)) {
            conversationId = UUIDGenerator.generate();
        }
        
        AgentContext context = memorySystem.getContext(conversationId);
        
        if (context == null) {
            context = AgentContext.builder()
                .conversationId(conversationId)
                .agentId(StringUtils.getString(request.getAgentId(), AgentConstants.DEFAULT_AGENT_ID))
                .messageDTOs(new java.util.ArrayList<>())
                .toolCallHistory(new java.util.ArrayList<>())
                .ragRetrieveHistory(new java.util.ArrayList<>())
                .iterations(0)
                .build();
        }
        
        return context;
    }
    
    /**
     * 生成对话标题
     */
    private String generateTitle(String content) {
        if (StringUtils.isEmpty(content)) {
            return "新对话";
        }
        
        String title = content.length() > 30 ? content.substring(0, 30) : content;
        return title.trim();
    }
    
    /**
     * 发送SSE事件
     */
    private void sendEvent(SseEmitter emitter, AgentEventData eventData) {
        try {
            String eventStr = JSON.toJSONString(eventData);
            emitter.send(SseEmitter.event()
                .name(eventData.getEvent())
                .data(eventStr));
            log.debug("发送Agent事件: {}", eventData.getEvent());
        } catch (IllegalStateException e) {
            // SSE连接已关闭，这是正常的（流式输出异步完成时可能发生）
            log.debug("SSE连接已关闭，忽略事件发送: {}", eventData.getEvent());
        } catch (IOException e) {
            log.error("发送SSE事件失败", e);
        }
    }
    
    /**
     * 关闭SSE连接
     */
    private void closeSSE(SseEmitter emitter, String requestId) {
        try {
            sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_COMPLETE)
                .message("任务完成")
                .build());
        } catch (Exception e) {
            log.error("发送完成事件失败", e);
        } finally {
            LocalCache.remove(AgentConstants.CACHE_PREFIX_AGENT_SSE, requestId);
            try {
                emitter.complete();
            } catch (Exception ignore) {}
        }
    }
    
    @Override
    public boolean stop(String requestId) {
        SseEmitter emitter = LocalCache.get(AgentConstants.CACHE_PREFIX_AGENT_SSE, requestId);
        if (emitter != null) {
            closeSSE(emitter, requestId);
            return true;
        }
        return false;
    }
    
    @Override
    public boolean clearMemory(String conversationId) {
        memorySystem.clearMemory(conversationId);
        return true;
    }
}

