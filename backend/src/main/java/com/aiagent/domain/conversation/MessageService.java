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
     * 获取会话消息列表（仅返回对话展示有意义的 user / assistant 消息）
     *
     * <p>过滤规则：
     * <ul>
     *   <li>{@code role=tool} — 工具执行结果消息，执行过程已内嵌到对应 assistant 消息的
     *       {@code metadata.executionProcess}，此处无需单独展示。</li>
     *   <li>{@code role=assistant + content 为空 + metadata.messageData.toolExecutionRequests 非空} —
     *       LangChain4j 产生的工具调用请求消息（AiMessage with tool_calls），其信息同样内嵌在
     *       {@code executionProcess} 里，对用户无展示价值。</li>
     * </ul>
     */
    public List<MessageResponse> getMessages(String conversationId, int limit) {
        List<MessageEntity> entities = messageMapper.selectByConversationId(conversationId, limit);

        return entities.stream()
            .filter(this::isDisplayableMessage)
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    /**
     * 判断消息是否需要展示给前端
     * 过滤掉工具执行结果消息和空内容的工具调用请求消息（这些已内嵌到 executionProcess 中）
     */
    private boolean isDisplayableMessage(MessageEntity entity) {
        // 过滤掉 role=tool 的工具执行结果消息
        if ("tool".equals(entity.getRole())) {
            return false;
        }

        // 过滤掉 role=assistant 且 content 为空且包含 toolExecutionRequests 的中间消息
        if ("assistant".equals(entity.getRole())
                && (entity.getContent() == null || entity.getContent().isBlank())) {
            String metadata = entity.getMetadata();
            if (metadata != null && metadata.contains("toolExecutionRequests")) {
                try {
                    com.alibaba.fastjson2.JSONObject meta = JSON.parseObject(metadata);
                    com.alibaba.fastjson2.JSONObject messageData = meta.getJSONObject("messageData");
                    if (messageData != null
                            && messageData.containsKey("toolExecutionRequests")
                            && !messageData.getJSONArray("toolExecutionRequests").isEmpty()) {
                        return false;
                    }
                } catch (Exception ignored) {
                    // 解析失败则保留该消息
                }
            }
        }

        return true;
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

