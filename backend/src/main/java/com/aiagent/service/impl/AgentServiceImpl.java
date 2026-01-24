package com.aiagent.service.impl;

import com.aiagent.config.AgentConfig;
import com.aiagent.constant.AgentConstants;
import com.aiagent.service.*;
import com.aiagent.service.memory.MemorySystem;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

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
    
    @Autowired
    private AgentConfig agentConfig;
    
    @Autowired
    private ConversationService conversationService;
    
    @Autowired
    private com.aiagent.service.rag.RAGEnhancer ragEnhancer;
    
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
            .message("zeno agent 启动中")
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
                String conversationId = context.getConversationId();
                
        // 1.1 确保会话在MySQL中存在（如果不存在则创建）
        ensureConversationExists(conversationId, request);
        
        // 2. 保存用户消息到记忆和MySQL（统一通过MemorySystem处理）
        UserMessage userMessage = new UserMessage(request.getContent());
        memorySystem.saveShortTermMemory(conversationId, userMessage, null, null, null, null);
        if (context.getMessages() == null) {
            context.setMessages(new java.util.ArrayList<>());
        }
        context.getMessages().add(userMessage);
        
        // 3. 设置上下文变量（使用具体属性）
        // 智能选择模型：如果未指定modelId，使用配置的默认模型
        String modelId = request.getModelId();
        if (modelId == null || modelId.trim().isEmpty()) {
            modelId = agentConfig.getModel().getDefaultModelId();
            log.info("未指定模型，使用默认模型: {}", modelId);
            
            // 发送模型选择事件
            sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_THINKING)
                .message("使用默认模型: " + modelId)
                .conversationId(context.getConversationId())
                .build());
        } else {
            log.info("使用指定模型: {}", modelId);
        }
        context.setModelId(modelId);
        context.setEnabledMcpGroups(request.getEnabledMcpGroups());
        context.setKnowledgeIds(request.getKnowledgeIds());
        // 设置启用的工具名称列表（为空则允许所有工具）
        context.setEnabledTools(request.getEnabledTools());
        context.setRequestId(requestId);
        
        // 设置事件发布器，用于各个Engine向前端发送进度事件
        context.setEventPublisher(eventData -> {
            // 自动填充requestId和conversationId
            if (eventData.getRequestId() == null) {
                eventData.setRequestId(requestId);
            }
            if (eventData.getConversationId() == null) {
                eventData.setConversationId(context.getConversationId());
            }
            sendEvent(emitter, eventData);
        });
        
        // 3.1 设置流式输出回调（使用CountDownLatch确保流式完成后再关闭SSE）
        CountDownLatch streamingCompleteLatch = new CountDownLatch(1);
        context.setStreamingCallback(new StreamingCallback() {
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
            }
        });
        
        // 3.2 如果有知识库，执行预检索（仅在第一次请求时）
        if (context.getKnowledgeIds() != null && !context.getKnowledgeIds().isEmpty()) {
            performInitialRagRetrieval(request, context, requestId, emitter);
        }
        
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
            // 保存AI回复到记忆和MySQL（统一通过MemorySystem处理）
            dev.langchain4j.data.message.AiMessage aiMessage = 
                new dev.langchain4j.data.message.AiMessage(finalResult.getData().toString());
            context.getMessages().add(aiMessage);
            
            // 提取元数据
            Map<String, Object> metadata = null;
            if (finalResult.getMetadata() instanceof Map) {
                metadata = (Map<String, Object>) finalResult.getMetadata();
            }
            
            // 保存到Redis和MySQL（包含模型ID和元数据）
            memorySystem.saveShortTermMemory(
                context.getConversationId(), 
                aiMessage, 
                context.getModelId(),
                null, // tokens - 可以从finalResult中获取
                null, // duration - 可以从finalResult中获取
                metadata
            );
            
            // 更新对话消息数量（Redis和MySQL都要更新）
            conversationStorage.incrementMessageCount(context.getConversationId());
            try {
                conversationService.incrementMessageCount(context.getConversationId());
            } catch (Exception e) {
                log.warn("更新MySQL消息数量失败: conversationId={}", context.getConversationId(), e);
            }
            
            // 注意：如果是LLM_GENERATE且使用了流式输出，内容已经通过callback发送了
            // 这里不再重复发送完整内容，只在metadata中标记streaming为false的情况下才发送
            boolean isStreaming = false;
            if (metadata != null && metadata.containsKey("streaming")) {
                isStreaming = Boolean.TRUE.equals(metadata.get("streaming"));
            }
            
            // 如果actionType是complete或direct_response，说明目标已达成，内容应该已经通过之前的动作发送过了
            // 不需要再发送，避免重复
            String actionType = finalResult.getActionType();
            boolean shouldSendMessage = !isStreaming && 
                                       !"complete".equals(actionType) && 
                                       !"direct_response".equals(actionType);
            
            if (shouldSendMessage) {
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
     * 执行初始 RAG 检索（仅在第一次请求时）
     */
    private void performInitialRagRetrieval(
            AgentRequest request, 
            AgentContext context, 
            String requestId, 
            SseEmitter emitter) {
        
        try {
            String query = request.getContent();
            List<String> knowledgeIds = context.getKnowledgeIds();
            
            if (StringUtils.isEmpty(query) || knowledgeIds == null || knowledgeIds.isEmpty()) {
                return;
            }
            
            // 发送检索开始事件
            sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_RAG_QUERYING)
                .message("正在检索知识库...")
                .conversationId(context.getConversationId())
                .build());
            
            // 执行 RAG 检索
            com.aiagent.vo.AgentKnowledgeResult ragResult = ragEnhancer.retrieve(query, knowledgeIds);
            
            // 记录检索历史并保存结果
            if (ragResult != null && ragResult.isNotEmpty()) {
                memorySystem.recordRAGRetrieve(
                    context, 
                    query, 
                    knowledgeIds, 
                    ragResult.getTotalCount()
                );
                
                // 保存检索结果到 context
                context.setInitialRagResult(ragResult);
                
                // 发送检索完成事件（用于前端展示）
                sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_RAG_RETRIEVE)
                    .data(ragResult)
                    .message("检索到 " + ragResult.getTotalCount() + " 条相关知识")
                    .conversationId(context.getConversationId())
                    .build());
                
                log.info("预检索完成，检索到 {} 条知识", ragResult.getTotalCount());
            } else {
                log.info("预检索完成，未检索到相关知识");
            }
            
        } catch (Exception e) {
            log.warn("预检索失败，不影响后续流程", e);
            // 预检索失败不应该影响主流程，只记录日志
        }
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
        
        // 从请求中更新配置（每次请求都可能更新工具选择等配置）
        if (StringUtils.isNotEmpty(request.getModelId())) {
            context.setModelId(request.getModelId());
        }
        if (request.getKnowledgeIds() != null) {
            context.setKnowledgeIds(request.getKnowledgeIds());
        }
        if (request.getEnabledMcpGroups() != null) {
            context.setEnabledMcpGroups(request.getEnabledMcpGroups());
        }
        // 设置启用的工具名称列表（为空则允许所有工具）
        if (request.getEnabledTools() != null) {
            context.setEnabledTools(request.getEnabledTools());
        }
        if (request.getMode() != null) {
            context.setMode(request.getMode());
        }
        
        return context;
    }
    
    /**
     * 确保会话在MySQL中存在（如果不存在则创建）
     */
    private void ensureConversationExists(String conversationId, AgentRequest request) {
        try {
            // 检查会话是否在MySQL中存在
            ConversationInfo existingConversation = conversationService.getConversation(conversationId);
            
            if (existingConversation == null) {
                // 会话不存在，尝试从Redis加载
                Map<String, Object> redisConversation = conversationStorage.getConversation(conversationId);
                
                ConversationInfo conversationInfo;
                if (redisConversation != null && !redisConversation.isEmpty()) {
                    // 从Redis加载并转换
                    conversationInfo = ConversationInfo.builder()
                        .id(conversationId)
                        .title(redisConversation.get("title") != null ? redisConversation.get("title").toString() : "新对话")
                        .status(redisConversation.get("status") != null ? redisConversation.get("status").toString() : "active")
                        .messageCount(redisConversation.get("messageCount") != null ? 
                            Integer.parseInt(redisConversation.get("messageCount").toString()) : 0)
                        .modelId(redisConversation.get("modelId") != null ? redisConversation.get("modelId").toString() : null)
                        .modelName(redisConversation.get("modelName") != null ? redisConversation.get("modelName").toString() : null)
                        .build();
                } else {
                    // Redis中也不存在，创建新会话
                    conversationInfo = ConversationInfo.builder()
                        .id(conversationId)
                        .title(generateTitle(request.getContent()))
                        .status("active")
                        .messageCount(0)
                        .build();
                    
                    // 同时保存到Redis
                    conversationStorage.saveConversation(conversationInfo);
                }
                
                // 保存到MySQL
                conversationService.createConversation(conversationInfo);
                log.info("确保会话存在: conversationId={}, 已创建到MySQL", conversationId);
            }
        } catch (Exception e) {
            log.error("确保会话存在失败: conversationId={}", conversationId, e);
            // 即使失败也继续执行，避免影响主流程
        }
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

