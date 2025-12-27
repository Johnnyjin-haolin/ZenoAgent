package com.aiagent.service.impl;

import com.aiagent.constant.AgentConstants;
import com.aiagent.model.AgentState;
import com.aiagent.service.*;
import com.aiagent.storage.ConversationStorage;
import com.aiagent.util.LocalCache;
import com.aiagent.util.StringUtils;
import com.aiagent.util.UUIDGenerator;
import com.aiagent.vo.*;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
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
                Map<String, Object> conversationData = new HashMap<>();
                conversationData.put("id", conversationId);
                conversationData.put("title", generateTitle(request.getContent()));
                conversationData.put("status", "active");
                conversationData.put("messageCount", 0);
                
                conversationStorage.saveConversation(conversationId, conversationData);
                request.setConversationId(conversationId);
                
                log.info("创建新对话: conversationId={}", conversationId);
            } catch (Exception e) {
                log.error("创建对话失败", e);
            }
        }
        
        // 2. 保存用户消息到记忆
        dev.langchain4j.data.message.UserMessage userMessage = 
            new dev.langchain4j.data.message.UserMessage(request.getContent());
        memorySystem.saveShortTermMemory(context.getConversationId(), userMessage);
        if (context.getMessages() == null) {
            context.setMessages(new java.util.ArrayList<>());
        }
        context.getMessages().add(userMessage);
        
        // 3. 设置上下文变量
        if (context.getVariables() == null) {
            context.setVariables(new HashMap<>());
        }
        context.getVariables().put("modelId", 
            StringUtils.getString(request.getModelId(), "gpt-4o-mini"));
        context.getVariables().put("enabledMcpGroups", request.getEnabledMcpGroups());
        context.getVariables().put("knowledgeIds", request.getKnowledgeIds());
        
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
            
            sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_MESSAGE)
                .content(finalResult.getData().toString())
                .conversationId(context.getConversationId())
                .build());
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
        
        // 8. 关闭SSE
        closeSSE(emitter, requestId);
    }
    
    /**
     * 加载或创建上下文
     */
    private AgentContext loadOrCreateContext(AgentRequest request) {
        String conversationId = StringUtils.getString(
            request.getConversationId(), 
            UUIDGenerator.generate()
        );
        
        AgentContext context = memorySystem.getContext(conversationId);
        
        if (context == null) {
            context = AgentContext.builder()
                .conversationId(conversationId)
                .agentId(StringUtils.getString(request.getAgentId(), AgentConstants.DEFAULT_AGENT_ID))
                .messageDTOs(new java.util.ArrayList<>())
                .toolCallHistory(new java.util.ArrayList<>())
                .ragRetrieveHistory(new java.util.ArrayList<>())
                .iterations(0)
                .variables(new HashMap<>())
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

