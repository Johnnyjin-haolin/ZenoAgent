package com.aiagent.service;

import com.aiagent.constant.AgentConstants;
import com.aiagent.util.StringUtils;
import com.aiagent.vo.AgentContext;
import com.aiagent.vo.MessageDTO;
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
     * 保存短期记忆（对话历史）
     * 
     * @param conversationId 会话ID
     * @param message 消息
     */
    public void saveShortTermMemory(String conversationId, ChatMessage message) {
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
            
            log.debug("保存短期记忆: conversationId={}, messageCount={}", conversationId, messageDTOs.size());
            
        } catch (Exception e) {
            log.error("保存短期记忆失败", e);
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
            redisTemplate.opsForValue().set(key, context, WORK_MEMORY_EXPIRE_HOURS, TimeUnit.HOURS);
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
     * @param context Agent上下文
     * @param toolName 工具名称
     * @param params 参数
     * @param result 结果
     */
    public void recordToolCall(AgentContext context, String toolName, 
                              Map<String, Object> params, Object result) {
        if (context.getToolCallHistory() == null) {
            context.setToolCallHistory(new ArrayList<>());
        }
        
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("toolName", toolName);
        record.put("params", params);
        record.put("result", result);
        record.put("timestamp", System.currentTimeMillis());
        
        context.getToolCallHistory().add(record);
        
        log.debug("记录工具调用: toolName={}", toolName);
    }
    
    /**
     * 记录RAG检索历史
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


