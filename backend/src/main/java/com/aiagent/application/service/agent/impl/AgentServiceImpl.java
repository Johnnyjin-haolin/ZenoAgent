package com.aiagent.application.service.agent.impl;

import com.aiagent.infrastructure.config.AgentConfig;
import com.aiagent.shared.constant.AgentConstants;
import com.aiagent.application.service.agent.AgentContextService;
import com.aiagent.application.service.agent.AgentStateMachine;
import com.aiagent.application.service.agent.AgentStreamingService;
import com.aiagent.application.service.conversation.ConversationService;
import com.aiagent.application.service.agent.IAgentService;
import com.aiagent.application.service.engine.ReActEngine;
import com.aiagent.application.service.engine.ReActExecutionResult;
import com.aiagent.application.service.StreamingCallback;
import com.aiagent.application.service.memory.MemorySystem;
import com.aiagent.api.dto.AgentEventData;
import com.aiagent.api.dto.AgentRequest;
import com.aiagent.application.model.AgentContext;
import com.aiagent.infrastructure.storage.ConversationStorage;
import com.aiagent.shared.util.UUIDGenerator;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private static final long STEP_EVENT_THRESHOLD_MS = 300;
    
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
    private AgentContextService agentContextService;

    @Autowired
    private AgentStreamingService streamingService;
    
    @Override
    //todo 前端停止生成后，后端react没有终止。

    public SseEmitter execute(AgentRequest request) {
        log.info("开始执行Agent任务（ReAct架构）: {}", request.getContent());
        
        // 标准化会话ID：前端临时ID不直接入库
        request.setConversationId(agentContextService.normalizeConversationId(request.getConversationId()));
        
        // 1. 创建SSE连接
        String requestId = UUIDGenerator.generate();
        SseEmitter emitter = streamingService.createEmitter(requestId);
        
        // 3. 发送初始事件
        streamingService.sendEvent(emitter, AgentEventData.builder()
            .requestId(requestId)
            .event(AgentConstants.EVENT_AGENT_START)
            .message(AgentConstants.MESSAGE_AGENT_START)
            .conversationId(request.getConversationId())
            .build());
        
        // 4. 缓存emitter
        streamingService.cacheEmitter(requestId, emitter);
        
        // 5. 异步执行任务
        CompletableFuture.runAsync(() -> {
            try {
                executeWithReAct(request, requestId, emitter);
            } catch (Exception e) {
                log.error("Agent任务执行失败", e);
                streamingService.sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_ERROR)
                    .message("执行失败: " + e.getMessage())
                    .conversationId(request.getConversationId())
                    .build());
                streamingService.closeEmitter(emitter, requestId);
            }
        });
        
        return emitter;
    }
    
    /**
     * 使用ReAct循环执行任务
     */
    private void executeWithReAct(AgentRequest request, String requestId, SseEmitter emitter) {
        long totalStartNs = System.nanoTime();
        long stepStartNs = System.nanoTime();
        // 1. 加载或创建上下文
        AgentContext context = agentContextService.loadOrCreateContext(request);
        String conversationId = context.getConversationId();
        stepStartNs = logStep("load_context", stepStartNs, requestId, conversationId, null, emitter);
                
        // 1.1 确保会话在MySQL中存在（如果不存在则创建）
        agentContextService.ensureConversationExists(conversationId, request);
        stepStartNs = logStep("ensure_conversation", stepStartNs, requestId, conversationId, null, emitter);
        
        // 2. 保存用户消息到记忆和MySQL（统一通过MemorySystem处理）
        UserMessage userMessage = new UserMessage(request.getContent());
        memorySystem.saveShortTermMemory(conversationId, userMessage, null, null, null, null);
        if (context.getMessages() == null) {
            context.setMessages(new java.util.ArrayList<>());
        }
        context.getMessages().add(userMessage);
        stepStartNs = logStep("save_user_message", stepStartNs, requestId, conversationId, null, emitter);
        
        // 3. 设置上下文变量（使用具体属性）
        // 智能选择模型：如果未指定modelId，使用配置的默认模型
        String modelId = request.getModelId();
        if (modelId == null || modelId.trim().isEmpty()) {
            modelId = agentConfig.getModel().getDefaultModelId();
            log.info("未指定模型，使用默认模型: {}", modelId);
            
            // 发送模型选择事件
            streamingService.sendEvent(emitter, AgentEventData.builder()
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
        stepStartNs = logStep("init_context_vars", stepStartNs, requestId, conversationId,
            "modelId=" + modelId, emitter);
        
        // 设置事件发布器，用于各个Engine向前端发送进度事件
        context.setEventPublisher(eventData -> {
            // 自动填充requestId和conversationId
            if (eventData.getRequestId() == null) {
                eventData.setRequestId(requestId);
            }
            if (eventData.getConversationId() == null) {
                eventData.setConversationId(context.getConversationId());
            }
            streamingService.sendEvent(emitter, eventData);
        });
        
        // 3.1 设置流式输出回调（使用CountDownLatch确保流式完成后再关闭SSE）
        CountDownLatch streamingCompleteLatch = new CountDownLatch(1);
        context.setStreamingCallback(new StreamingCallback() {
            @Override
            public void onToken(String token) {
                // 实时发送token到前端
                streamingService.sendEvent(emitter, AgentEventData.builder()
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
                streamingService.sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_STREAM_COMPLETE)
                .message(AgentConstants.MESSAGE_STREAM_COMPLETE)
                    .conversationId(context.getConversationId())
                    .build());
                // 释放锁，允许主线程继续执行并关闭SSE
                streamingCompleteLatch.countDown();
            }
            
            @Override
            public void onError(Throwable error) {
                log.error("LLM流式输出错误", error);
                streamingService.sendEvent(emitter, AgentEventData.builder()
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
        stepStartNs = logStep("init_streaming_callback", stepStartNs, requestId, conversationId, null, emitter);
        
        // 3.2 如果有知识库，执行预检索（仅在第一次请求时）
        if (context.getKnowledgeIds() != null && !context.getKnowledgeIds().isEmpty()) {
            long ragStartNs = System.nanoTime();
            agentContextService.performInitialRagRetrieval(
                request,
                context,
                requestId,
                eventData -> streamingService.sendEvent(emitter, eventData)
            );
            stepStartNs = logStep("rag_pre_retrieval", ragStartNs, requestId, conversationId,
                "knowledgeCount=" + context.getKnowledgeIds().size(), emitter);
        }
        
        // 4. 注册状态变更监听器
        stateMachine.initialize(context, newState -> {
            streamingService.sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_THINKING)
                .message("状态: " + newState.getDescription())
                .conversationId(context.getConversationId())
                .build());
        });
        stepStartNs = logStep("init_state_machine", stepStartNs, requestId, conversationId, null, emitter);
        
        // 5. 执行ReAct循环
        streamingService.sendEvent(emitter, AgentEventData.builder()
            .requestId(requestId)
            .event(AgentConstants.EVENT_AGENT_THINKING)
            .message("开始ReAct循环...")
            .conversationId(context.getConversationId())
            .build());
        
        long reactStartNs = System.nanoTime();
        //todo 简单咨询模式
        //todo react 复杂任务模式
        ReActExecutionResult executionResult = reActEngine.execute(request.getContent(), context);
        stepStartNs = logStep("react_execute", reactStartNs, requestId, conversationId,
            "modelId=" + modelId + ", iterations=" + executionResult.getIterations(), emitter);
        
        // 6. 处理最终结果
        // 从执行结果中获取所有对话消息（包括用户消息和AI回复消息）
        List<ChatMessage> allMessages = executionResult.getMessages();
        
        if (allMessages != null && !allMessages.isEmpty()) {
            // 保存所有AI回复消息到记忆和MySQL
            for (ChatMessage message : allMessages) {
                // 只保存AI消息（用户消息已经在步骤2中保存）
                if (message instanceof AiMessage) {
                    AiMessage aiMessage = (AiMessage) message;
                    
                    // 提取元数据（如果有）
                    Map<String, Object> metadata = executionResult.getMetadata();
                    
                    // 保存到Redis和MySQL（包含模型ID和元数据）
                    memorySystem.saveShortTermMemory(
                        context.getConversationId(),
                        aiMessage,
                        context.getModelId(),
                        null, // tokens - 可以从metadata中获取
                        null, // duration - 可以从executionResult中获取
                        metadata
                    );
                    log.debug("保存AI消息到记忆系统，内容长度: {}", aiMessage.text().length());
                }
            }
            stepStartNs = logStep("save_ai_messages", stepStartNs, requestId, conversationId, 
                "messageCount=" + allMessages.size(), emitter);
        } else {
            log.warn("执行结果中没有对话消息");
        }

        // 更新对话消息数量（Redis和MySQL都要更新）
        conversationStorage.incrementMessageCount(context.getConversationId());
        try {
            conversationService.incrementMessageCount(context.getConversationId());
        } catch (Exception e) {
            log.warn("更新MySQL消息数量失败: conversationId={}", context.getConversationId(), e);
        }
        stepStartNs = logStep("increment_message_count", stepStartNs, requestId, conversationId, null, emitter);
            

        // 7. 保存上下文
        memorySystem.saveContext(context);
        stepStartNs = logStep("save_context", stepStartNs, requestId, conversationId, null, emitter);
        
        // 8. 关闭SSE（此时所有流式事件都已发送完成）
        streamingService.closeEmitter(emitter, requestId);
        logStep("close_emitter", System.nanoTime(), requestId, conversationId, null, emitter);
        logStep("total", totalStartNs, requestId, conversationId, null, emitter);
    }
    
    @Override
    public boolean stop(String requestId) {
        SseEmitter emitter = streamingService.getEmitter(requestId);
        if (emitter != null) {
            streamingService.closeEmitter(emitter, requestId);
            return true;
        }
        return false;
    }
    
    @Override
    public boolean clearMemory(String conversationId) {
        memorySystem.clearMemory(conversationId);
        return true;
    }

    private long logStep(String step, long startNs, String requestId, String conversationId,
                         String extra, SseEmitter emitter) {
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        String extraInfo = (extra == null || extra.isEmpty()) ? "-" : extra;
        log.info("agent_step|requestId={} conversationId={} step={} durationMs={} extra={}",
            requestId, conversationId, step, durationMs, extraInfo);

        if (durationMs >= STEP_EVENT_THRESHOLD_MS && emitter != null) {
            streamingService.sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_THINKING)
                .message("步骤耗时: " + step + " " + durationMs + "ms")
                .conversationId(conversationId)
                .build());
        }
        return System.nanoTime();
    }
}

