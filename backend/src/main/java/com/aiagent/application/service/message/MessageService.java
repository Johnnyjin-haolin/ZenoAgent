package com.aiagent.application.service.message;

import com.aiagent.api.dto.MessageDTO;
import com.aiagent.domain.entity.MessageEntity;
import com.aiagent.infrastructure.mapper.MessageMapper;
import com.aiagent.shared.util.UUIDGenerator;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
     */
    public void saveMessage(String conversationId, ChatMessage message, String modelId, 
                           Integer tokens, Integer duration, Map<String, Object> metadata) {
        MessageEntity entity = new MessageEntity();
        entity.setConversationId(conversationId);
        entity.setMessageId(UUIDGenerator.generate());
        entity.setRole(message.type().toString().toLowerCase());
        
        // 根据消息类型获取内容
        String content = "";
        if (message instanceof dev.langchain4j.data.message.UserMessage) {
            content = ((dev.langchain4j.data.message.UserMessage) message).singleText();
        } else if (message instanceof dev.langchain4j.data.message.AiMessage) {
            content = ((dev.langchain4j.data.message.AiMessage) message).text();
        } else if (message instanceof dev.langchain4j.data.message.SystemMessage) {
            content = ((dev.langchain4j.data.message.SystemMessage) message).text();
        }
        
        entity.setContent(content);
        entity.setModelId(modelId);
        entity.setTokens(tokens);
        entity.setDuration(duration);
        
        if (metadata != null && !metadata.isEmpty()) {
            entity.setMetadata(JSON.toJSONString(metadata));
        }
        
        messageMapper.insert(entity);
        log.debug("保存消息: conversationId={}, role={}", conversationId, entity.getRole());
    }
    
    /**
     * 获取会话消息列表
     */
    public List<MessageDTO> getMessages(String conversationId, int limit) {
        List<MessageEntity> entities = messageMapper.selectByConversationId(conversationId, limit);
        
        return entities.stream()
            .map(this::convertToDTO)
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
     * 转换为MessageDTO
     */
    private MessageDTO convertToDTO(MessageEntity entity) {
        MessageDTO dto = new MessageDTO();
        dto.setId(entity.getMessageId());
        dto.setRole(entity.getRole());
        dto.setContent(entity.getContent());
        dto.setModelId(entity.getModelId());
        dto.setTokens(entity.getTokens());
        dto.setDuration(entity.getDuration());
        dto.setCreateTime(entity.getCreateTime());
        
        if (entity.getMetadata() != null && !entity.getMetadata().isEmpty()) {
            dto.setMetadata(JSON.parseObject(entity.getMetadata()));
        }
        
        return dto;
    }
}

