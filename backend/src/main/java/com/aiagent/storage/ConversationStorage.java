package com.aiagent.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 对话存储（Redis实现）
 * 替代MySQL存储对话信息
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class ConversationStorage {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String CONVERSATION_PREFIX = "aiagent:conversation:";
    private static final long CONVERSATION_TTL_DAYS = 7;
    
    /**
     * 保存对话信息
     */
    public void saveConversation(String conversationId, Map<String, Object> conversationData) {
        try {
            String key = CONVERSATION_PREFIX + conversationId;
            
            // 设置创建时间
            if (!conversationData.containsKey("createTime")) {
                conversationData.put("createTime", new Date());
            }
            conversationData.put("updateTime", new Date());
            
            redisTemplate.opsForHash().putAll(key, conversationData);
            redisTemplate.expire(key, CONVERSATION_TTL_DAYS, TimeUnit.DAYS);
            
            log.debug("保存对话: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("保存对话失败: conversationId={}", conversationId, e);
        }
    }
    
    /**
     * 获取对话信息
     */
    public Map<String, Object> getConversation(String conversationId) {
        try {
            String key = CONVERSATION_PREFIX + conversationId;
            Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
            
            if (hash == null || hash.isEmpty()) {
                return null;
            }
            
            Map<String, Object> result = new HashMap<>();
            hash.forEach((k, v) -> result.put(k.toString(), v));
            
            return result;
        } catch (Exception e) {
            log.error("获取对话失败: conversationId={}", conversationId, e);
            return null;
        }
    }
    
    /**
     * 更新对话标题
     */
    public boolean updateConversationTitle(String conversationId, String title) {
        try {
            String key = CONVERSATION_PREFIX + conversationId;
            redisTemplate.opsForHash().put(key, "title", title);
            redisTemplate.opsForHash().put(key, "updateTime", new Date());
            return true;
        } catch (Exception e) {
            log.error("更新对话标题失败: conversationId={}", conversationId, e);
            return false;
        }
    }
    
    /**
     * 更新对话状态
     */
    public boolean updateConversationStatus(String conversationId, String status) {
        try {
            String key = CONVERSATION_PREFIX + conversationId;
            redisTemplate.opsForHash().put(key, "status", status);
            redisTemplate.opsForHash().put(key, "updateTime", new Date());
            return true;
        } catch (Exception e) {
            log.error("更新对话状态失败: conversationId={}", conversationId, e);
            return false;
        }
    }
    
    /**
     * 删除对话
     */
    public boolean deleteConversation(String conversationId) {
        try {
            String key = CONVERSATION_PREFIX + conversationId;
            redisTemplate.delete(key);
            log.info("删除对话: conversationId={}", conversationId);
            return true;
        } catch (Exception e) {
            log.error("删除对话失败: conversationId={}", conversationId, e);
            return false;
        }
    }
    
    /**
     * 增加消息数量
     */
    public void incrementMessageCount(String conversationId) {
        try {
            String key = CONVERSATION_PREFIX + conversationId;
            redisTemplate.opsForHash().increment(key, "messageCount", 1);
            redisTemplate.opsForHash().put(key, "updateTime", new Date());
        } catch (Exception e) {
            log.error("增加消息数量失败: conversationId={}", conversationId, e);
        }
    }
    
    /**
     * 获取所有对话列表
     * 注意：Redis SCAN操作可能性能较低，适合对话数量不多的场景
     * 
     * @param status 状态筛选（可选）
     * @return 对话列表
     */
    public List<Map<String, Object>> listConversations(String status) {
        List<Map<String, Object>> conversations = new ArrayList<>();
        
        try {
            // 使用Redis SCAN查找所有对话key
            Set<String> keys = redisTemplate.keys(CONVERSATION_PREFIX + "*");
            
            if (keys == null || keys.isEmpty()) {
                return conversations;
            }
            
            // 遍历所有对话
            for (String key : keys) {
                Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
                
                if (hash == null || hash.isEmpty()) {
                    continue;
                }
                
                // 转换为Map
                Map<String, Object> conversation = new HashMap<>();
                hash.forEach((k, v) -> conversation.put(k.toString(), v));
                
                // 状态筛选
                if (status != null && !status.isEmpty()) {
                    String convStatus = (String) conversation.get("status");
                    if (!status.equals(convStatus)) {
                        continue;
                    }
                }
                
                conversations.add(conversation);
            }
            
            // 按更新时间降序排序
            conversations.sort((a, b) -> {
                Date dateA = (Date) a.get("updateTime");
                Date dateB = (Date) b.get("updateTime");
                if (dateA == null && dateB == null) return 0;
                if (dateA == null) return 1;
                if (dateB == null) return -1;
                return dateB.compareTo(dateA);
            });
            
        } catch (Exception e) {
            log.error("获取对话列表失败", e);
        }
        
        return conversations;
    }
}


