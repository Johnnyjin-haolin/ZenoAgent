package com.aiagent.domain.conversation;

import com.aiagent.api.dto.MessageResponse;
import com.aiagent.domain.model.bo.MessageBO;
import com.aiagent.domain.model.entity.MessageEntity;
import com.aiagent.infrastructure.mapper.MessageMapper;
import com.aiagent.common.util.UUIDGenerator;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 消息服务
 * 
 * @author aiagent
 */
@Slf4j
@Service
public class MessageService {
    
    @Autowired
    private MessageMapper messageMapper;
    
    /**
     * 保存消息
     * 将 ChatMessage 通过 MessageDTO 完整序列化（含 tool_calls），存入 metadata.messageData
     * 支持无损还原所有 LangChain4j 消息类型
     */
    public void saveMessage(String conversationId, ChatMessage message, String modelId,
                           Integer tokens, Integer duration, Map<String, Object> customMetadata) {
        // 1. 通过 MessageDTO（BO）将 ChatMessage 完整序列化
        MessageBO messageBO = MessageBO.from(message);
        if (messageBO == null) {
            log.warn("消息序列化失败，跳过保存: conversationId={}", conversationId);
            return;
        }

        // 2. 构建 MessageEntity
        MessageEntity entity = new MessageEntity();
        entity.setConversationId(conversationId);
        entity.setMessageId(UUIDGenerator.generate());

        // role 映射：AI → assistant，TOOL_EXECUTION → tool，其他小写
        String role = switch (messageBO.getType()) {
            case "AI"             -> "assistant";
            case "USER"           -> "user";
            case "SYSTEM"         -> "system";
            case "TOOL_EXECUTION" -> "tool";
            default               -> messageBO.getType().toLowerCase();
        };
        entity.setRole(role);
        entity.setContent(messageBO.getText() != null ? messageBO.getText() : "");
        entity.setModelId(modelId);
        entity.setTokens(tokens);
        entity.setDuration(duration);

        // 3. 构建 metadata：合并 customMetadata + 完整 messageData（用于无损还原）
        Map<String, Object> fullMetadata = new HashMap<>();
        if (customMetadata != null && !customMetadata.isEmpty()) {
            fullMetadata.putAll(customMetadata);
        }
        fullMetadata.put("messageData", JSON.parseObject(JSON.toJSONString(messageBO)));
        entity.setMetadata(JSON.toJSONString(fullMetadata));

        messageMapper.insert(entity);
        log.debug("保存消息: conversationId={}, role={}", conversationId, role);
    }
    
    /**
     * 获取会话消息列表
     */
    public List<MessageResponse> getMessages(String conversationId, int limit) {
        List<MessageEntity> entities = messageMapper.selectByConversationId(conversationId, limit);
        
        return entities.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * 删除会话的所有消息
     */
    public void deleteMessages(String conversationId) {
        messageMapper.deleteByConversationId(conversationId);
        log.info("删除会话消息: conversationId={}", conversationId);
    }
    
    /**
     * 转换为 MessageResponse（API 响应对象）
     */
    private MessageResponse convertToResponse(MessageEntity entity) {
        MessageResponse response = new MessageResponse();
        response.setId(entity.getMessageId());
        response.setRole(entity.getRole());
        response.setContent(entity.getContent());
        response.setModelId(entity.getModelId());
        response.setTokens(entity.getTokens());
        response.setDuration(entity.getDuration());
        response.setCreateTime(entity.getCreateTime());
        
        if (entity.getMetadata() != null && !entity.getMetadata().isEmpty()) {
            response.setMetadata(JSON.parseObject(entity.getMetadata()));
        }
        
        return response;
    }
}

