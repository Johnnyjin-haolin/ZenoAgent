package com.aiagent.domain.memory;

import com.aiagent.application.StreamingCallback;
import com.aiagent.domain.model.bo.AgentKnowledgeResult;
import com.aiagent.common.constant.AgentConstants;
import com.aiagent.domain.conversation.MessageService;
import com.aiagent.common.util.StringUtils;
import com.aiagent.domain.model.bo.AgentContext;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 记忆系统
 * 管理Agent的工作记忆（基于Redis Context缓存）
 * 消息持久化统一使用MySQL，不再使用Redis存储消息
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class MemorySystem {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired(required = false)
    private MessageService messageService;
    
    /**
     * 工作记忆过期时间（小时）
     */
    private static final int WORK_MEMORY_EXPIRE_HOURS = 1;

    /**
     * 保存消息到MySQL（持久化）
     * 
     * @param conversationId 会话ID
     * @param message 消息
     * @param modelId 模型ID（可选，用于MySQL持久化）
     * @param tokens Token数量（可选）
     * @param duration 耗时（毫秒，可选）
     * @param metadata 元数据（可选，如工具调用、RAG结果等）
     */
    public void saveShortTermMemory(String conversationId, ChatMessage message,
                                   String modelId, Integer tokens, Integer duration,
                                   Map<String, Object> metadata) {
        // 保存到MySQL（持久化）
        if (messageService != null) {
            try {
                messageService.saveMessage(conversationId, message, modelId, tokens, duration, metadata);
            } catch (Exception e) {
                log.warn("保存消息到MySQL失败: conversationId={}", conversationId, e);
                // 不抛出异常，MySQL失败不影响主流程
            }
        }
    }

    /**
     * 保存上下文
     * 
     * @param context Agent上下文
     */
    public void saveContext(AgentContext context) {
        if (context == null || StringUtils.isEmpty(context.getConversationId())) {
            return;
        }
        
        String key = AgentConstants.CACHE_PREFIX_AGENT_CONTEXT + context.getConversationId();
        
        try {
            // 保存前显式清理 transient 字段，避免序列化错误
            // 这些字段已经有 @JsonIgnore，但为了兼容性和安全性，显式设为 null
            String requestId = context.getRequestId();
            StreamingCallback streamingCallback = context.getStreamingCallback();
            java.util.function.Consumer<com.aiagent.api.dto.AgentEventData> eventPublisher = context.getEventPublisher();
            AgentKnowledgeResult initialRagResult = context.getInitialRagResult();
            
            context.setRequestId(null);
            context.setStreamingCallback(null);
            context.setEventPublisher(null);
            context.setInitialRagResult(null);
            
            // 保存到 Redis
            redisTemplate.opsForValue().set(key, context, WORK_MEMORY_EXPIRE_HOURS, TimeUnit.HOURS);
            
            // 恢复 transient 字段（如果需要继续使用）
            context.setRequestId(requestId);
            context.setStreamingCallback(streamingCallback);
            context.setEventPublisher(eventPublisher);
            context.setInitialRagResult(initialRagResult);
            
            log.debug("保存Agent上下文: conversationId={}", context.getConversationId());
        } catch (Exception e) {
            log.error("保存Agent上下文失败", e);
        }
    }
    
    /**
     * 获取上下文
     * 
     * @param conversationId 会话ID
     * @return Agent上下文
     */
    public AgentContext getContext(String conversationId) {
        if (StringUtils.isEmpty(conversationId)) {
            return null;
        }
        String key = AgentConstants.CACHE_PREFIX_AGENT_CONTEXT + conversationId;
        
        try {
            return (AgentContext) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("获取Agent上下文失败", e);
            return null;
        }
    }
    
    /**
     * 清除会话上下文缓存
     * 
     * @param conversationId 会话ID
     */
    public void clearMemory(String conversationId) {
        String contextKey = AgentConstants.CACHE_PREFIX_AGENT_CONTEXT + conversationId;
        
        try {
            redisTemplate.delete(contextKey);
            log.info("清除会话上下文缓存: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("清除会话上下文缓存失败", e);
        }
    }

}






