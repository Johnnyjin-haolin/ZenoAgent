package com.aiagent.application.service.memory;

import com.aiagent.shared.constant.AgentConstants;
import com.aiagent.application.service.message.MessageService;
import com.aiagent.shared.util.StringUtils;
import com.aiagent.application.model.AgentContext;
import com.aiagent.application.model.MessageDTO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 记忆系统
 * 管理Agent的短期记忆、工作记忆和长期记忆（基于Redis）
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
     * 短期记忆过期时间（小时）
     */
    private static final int SHORT_TERM_EXPIRE_HOURS = 24;
    
    /**
     * 工作记忆过期时间（小时）
     */
    private static final int WORK_MEMORY_EXPIRE_HOURS = 1;
    
    /**
     * 默认上下文窗口大小
     */
    private static final int DEFAULT_CONTEXT_WINDOW = 10;
    
    /**
     * 保存短期记忆（对话历史）- 简化版本，兼容旧代码
     * 
     * @param conversationId 会话ID
     * @param message 消息
     */
    public void saveShortTermMemory(String conversationId, ChatMessage message) {
        saveShortTermMemory(conversationId, message, null, null, null, null);
    }
    
    /**
     * 保存短期记忆（Redis + MySQL双存储）
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
        // 1. 保存到Redis（短期记忆）
        saveToRedis(conversationId, message);
        
        // 2. 保存到MySQL（持久化，异步执行，避免影响主流程）
        if (messageService != null) {
            try {
                messageService.saveMessage(conversationId, message, modelId, tokens, duration, metadata);
            } catch (Exception e) {
                log.warn("保存消息到MySQL失败，但Redis已保存: conversationId={}", conversationId, e);
                // 不抛出异常，确保Redis保存成功即可，MySQL失败不影响主流程
            }
        }
    }
    
    /**
     * 保存消息到Redis（私有方法，提取公共逻辑）
     */
    private void saveToRedis(String conversationId, ChatMessage message) {
        String key = AgentConstants.CACHE_PREFIX_AGENT_MEMORY + conversationId;
        
        try {
            // 获取现有消息列表（DTO格式）
            @SuppressWarnings("unchecked")
            List<MessageDTO> messageDTOs = (List<MessageDTO>) redisTemplate.opsForValue().get(key);
            
            if (messageDTOs == null) {
                messageDTOs = new ArrayList<>();
            }
            
            // 转换为 DTO 并添加
            MessageDTO messageDTO = MessageDTO.from(message);
            if (messageDTO != null) {
                messageDTOs.add(messageDTO);
            }
            
            // 限制消息数量
            if (messageDTOs.size() > DEFAULT_CONTEXT_WINDOW * 2) {
                messageDTOs = new ArrayList<>(messageDTOs.subList(
                    messageDTOs.size() - DEFAULT_CONTEXT_WINDOW * 2, 
                    messageDTOs.size()
                ));
            }
            
            // 保存到Redis（保存DTO，可序列化）
            redisTemplate.opsForValue().set(key, messageDTOs, SHORT_TERM_EXPIRE_HOURS, TimeUnit.HOURS);
            
            log.debug("保存短期记忆到Redis: conversationId={}, messageCount={}", conversationId, messageDTOs.size());
            
        } catch (Exception e) {
            log.error("保存短期记忆到Redis失败", e);
        }
    }
    
    /**
     * 获取短期记忆
     * 
     * @param conversationId 会话ID
     * @param limit 限制数量
     * @return 消息列表
     */
    public List<ChatMessage> getShortTermMemory(String conversationId, int limit) {
        String key = AgentConstants.CACHE_PREFIX_AGENT_MEMORY + conversationId;
        
        try {
            @SuppressWarnings("unchecked")
            List<MessageDTO> messageDTOs = (List<MessageDTO>) redisTemplate.opsForValue().get(key);
            
            if (messageDTOs == null || messageDTOs.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 返回最近的N条消息
            int start = Math.max(0, messageDTOs.size() - limit);
            List<MessageDTO> recentDTOs = messageDTOs.subList(start, messageDTOs.size());
            
            // 转换回 ChatMessage
            return recentDTOs.stream()
                .map(MessageDTO::toChatMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("获取短期记忆失败", e);
            return new ArrayList<>();
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
            com.aiagent.application.service.StreamingCallback streamingCallback = context.getStreamingCallback();
            java.util.function.Consumer<com.aiagent.api.dto.AgentEventData> eventPublisher = context.getEventPublisher();
            com.aiagent.application.model.AgentKnowledgeResult initialRagResult = context.getInitialRagResult();
            
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
     * 清除会话记忆
     * 
     * @param conversationId 会话ID
     */
    public void clearMemory(String conversationId) {
        String memoryKey = AgentConstants.CACHE_PREFIX_AGENT_MEMORY + conversationId;
        String contextKey = AgentConstants.CACHE_PREFIX_AGENT_CONTEXT + conversationId;
        
        try {
            redisTemplate.delete(memoryKey);
            redisTemplate.delete(contextKey);
            log.info("清除会话记忆: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("清除会话记忆失败", e);
        }
    }
    
    /**
     * 记录工具调用历史
     * 
     * @deprecated 已废弃，请使用统一的动作执行历史记录机制（ActionExecutor.recordActionExecutionHistory）
     * @param context Agent上下文
     * @param toolName 工具名称
     * @param params 参数
     * @param result 结果
     */
    @Deprecated
    public void recordToolCall(AgentContext context, String toolName, 
                              Map<String, Object> params, Object result) {
        // 该方法已废弃，动作执行历史现在由 ActionExecutor 统一记录到 actionExecutionHistory
        log.debug("recordToolCall 方法已废弃，工具调用历史现在由 ActionExecutor 统一记录");
    }

    private Object normalizeToolCallResult(Object result) {
        if (result == null) {
            return null;
        }
        if (isJsonSafeValue(result)) {
            return result;
        }
        if (isLangchainToolExecutionResult(result)) {
            return convertToolExecutionResult(result);
        }
        return String.valueOf(result);
    }

    private boolean isJsonSafeValue(Object value) {
        if (value == null) {
            return true;
        }
        return value instanceof CharSequence
            || value instanceof Number
            || value instanceof Boolean
            || value instanceof Character
            || value instanceof Map
            || value instanceof List
            || value.getClass().isArray();
    }

    private boolean isLangchainToolExecutionResult(Object value) {
        return "dev.langchain4j.service.tool.ToolExecutionResult"
            .equals(value.getClass().getName());
    }

    private Map<String, Object> convertToolExecutionResult(Object result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "ToolExecutionResult");
        data.put("isError", invokeNoArg(result, "isError"));
        Object resultText = invokeNoArg(result, "resultText");
        data.put("resultText", resultText != null ? String.valueOf(resultText) : null);
        Object rawResult = invokeNoArg(result, "result");
        data.put("result", isJsonSafeValue(rawResult) ? rawResult : String.valueOf(rawResult));
        return data;
    }

    private Object invokeNoArg(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception ex) {
            return null;
        }
    }
    
    /**
     * 记录RAG检索历史 todo 这些方法不适合放在记忆系统中，需要搞清楚记忆系统定位是什么
     * 
     * @param context Agent上下文
     * @param query 查询文本
     * @param knowledgeIds 知识库ID
     * @param resultCount 结果数量
     */
    public void recordRAGRetrieve(AgentContext context, String query, 
                                 List<String> knowledgeIds, int resultCount) {
        if (context.getRagRetrieveHistory() == null) {
            context.setRagRetrieveHistory(new ArrayList<>());
        }
        
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("query", query);
        record.put("knowledgeIds", knowledgeIds);
        record.put("resultCount", resultCount);
        record.put("timestamp", System.currentTimeMillis());
        
        context.getRagRetrieveHistory().add(record);
        
        log.debug("记录RAG检索: query={}, resultCount={}", query, resultCount);
    }
    
    /**
     * 构建消息摘要（用于向量记忆）
     * 
     * @param messages 消息列表
     * @return 摘要文本
     */
    public String buildMessageSummary(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        
        StringBuilder summary = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage) {
                summary.append("用户: ").append(((UserMessage) message).singleText()).append("\n");
            } else if (message instanceof AiMessage) {
                summary.append("助手: ").append(((AiMessage) message).text()).append("\n");
            }
        }
        
        return summary.toString();
    }
}


