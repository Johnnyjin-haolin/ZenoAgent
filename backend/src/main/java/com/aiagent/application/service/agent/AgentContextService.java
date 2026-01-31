package com.aiagent.application.service.agent;

import com.aiagent.shared.constant.AgentConstants;
import com.aiagent.application.service.memory.MemorySystem;
import com.aiagent.infrastructure.storage.ConversationStorage;
import com.aiagent.shared.util.StringUtils;
import com.aiagent.shared.util.UUIDGenerator;
import com.aiagent.application.model.AgentContext;
import com.aiagent.api.dto.AgentEventData;
import com.aiagent.application.model.AgentKnowledgeResult;
import com.aiagent.api.dto.AgentRequest;
import com.aiagent.api.dto.ConversationInfo;
import com.aiagent.application.service.conversation.ConversationService;
import com.aiagent.domain.entity.MessageEntity;
import com.aiagent.infrastructure.mapper.MessageMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent 上下文相关逻辑
 */
@Slf4j
@Service
public class AgentContextService {

    @Autowired
    private MemorySystem memorySystem;

    @Autowired
    private ConversationStorage conversationStorage;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private com.aiagent.application.service.rag.RAGEnhancer ragEnhancer;
    
    @Autowired
    private MessageMapper messageMapper;

    public String normalizeConversationId(String conversationId) {
        if (StringUtils.isEmpty(conversationId) || conversationId.startsWith("temp-")) {
            return UUIDGenerator.generate();
        }
        return conversationId;
    }

    public AgentContext loadOrCreateContext(AgentRequest request) {
        String conversationId = normalizeConversationId(request.getConversationId());

        AgentContext context = memorySystem.getContext(conversationId);

        if (context == null) {
            context = AgentContext.builder()
                .conversationId(conversationId)
                .agentId(StringUtils.getString(request.getAgentId(), AgentConstants.DEFAULT_AGENT_ID))
                .messageDTOs(new ArrayList<>())
                .actionExecutionHistory(new ArrayList<>())
                .ragRetrieveHistory(new ArrayList<>())
                .iterations(0)
                .build();
        }

        if (StringUtils.isNotEmpty(request.getModelId())) {
            context.setModelId(request.getModelId());
        }
        if (request.getKnowledgeIds() != null) {
            context.setKnowledgeIds(request.getKnowledgeIds());
        }
        if (request.getEnabledMcpGroups() != null) {
            context.setEnabledMcpGroups(request.getEnabledMcpGroups());
        }
        if (request.getEnabledTools() != null) {
            context.setEnabledTools(request.getEnabledTools());
        }
        if (request.getMode() != null) {
            context.setMode(request.getMode());
        }
        // 设置思考引擎配置（如果前端传入）
        if (request.getThinkingConfig() != null) {
            context.setThinkingConfig(request.getThinkingConfig());
        } else {
            // 如果前端未传入，使用默认配置
            context.setThinkingConfig(com.aiagent.api.dto.ThinkingConfig.builder().build());
        }
        
        // 加载历史对话消息（如果 messages 为空）
        if (context.getMessages() == null || context.getMessages().isEmpty()) {
            loadHistoryMessages(context, conversationId);
        }

        return context;
    }

    public void ensureConversationExists(String conversationId, AgentRequest request) {
        try {
            ConversationInfo existingConversation = conversationService.getConversation(conversationId);

            if (existingConversation == null) {
                Map<String, Object> redisConversation = conversationStorage.getConversation(conversationId);

                ConversationInfo conversationInfo;
                if (redisConversation != null && !redisConversation.isEmpty()) {
                    conversationInfo = ConversationInfo.builder()
                        .id(conversationId)
                        .title(redisConversation.get("title") != null ? redisConversation.get("title").toString() : AgentConstants.DEFAULT_CONVERSATION_TITLE)
                        .status(redisConversation.get("status") != null ? redisConversation.get("status").toString() : AgentConstants.DEFAULT_CONVERSATION_STATUS)
                        .messageCount(redisConversation.get("messageCount") != null ?
                            Integer.parseInt(redisConversation.get("messageCount").toString()) : 0)
                        .modelId(redisConversation.get("modelId") != null ? redisConversation.get("modelId").toString() : null)
                        .modelName(redisConversation.get("modelName") != null ? redisConversation.get("modelName").toString() : null)
                        .build();
                } else {
                    conversationInfo = ConversationInfo.builder()
                        .id(conversationId)
                        .title(generateTitle(request.getContent()))
                        .status(AgentConstants.DEFAULT_CONVERSATION_STATUS)
                        .messageCount(0)
                        .build();

                    conversationStorage.saveConversation(conversationInfo);
                }

                conversationService.createConversation(conversationInfo);
                log.info("确保会话存在: conversationId={}, 已创建到MySQL", conversationId);
            }
        } catch (Exception e) {
            log.error("确保会话存在失败: conversationId={}", conversationId, e);
        }
    }

    public void performInitialRagRetrieval(
            AgentRequest request,
            AgentContext context,
            String requestId,
            Consumer<AgentEventData> eventPublisher) {

        try {
            String query = request.getContent();
            List<String> knowledgeIds = context.getKnowledgeIds();

            if (StringUtils.isEmpty(query) || knowledgeIds == null || knowledgeIds.isEmpty()) {
                return;
            }

            eventPublisher.accept(AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_RAG_QUERYING)
                .message(AgentConstants.MESSAGE_RAG_QUERYING)
                .conversationId(context.getConversationId())
                .build());

            AgentKnowledgeResult ragResult = ragEnhancer.retrieve(query, knowledgeIds);

            if (ragResult != null && ragResult.isNotEmpty()) {
                memorySystem.recordRAGRetrieve(
                    context,
                    query,
                    knowledgeIds,
                    ragResult.getTotalCount()
                );

                context.setInitialRagResult(ragResult);

                eventPublisher.accept(AgentEventData.builder()
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
        }
    }

    private String generateTitle(String content) {
        if (StringUtils.isEmpty(content)) {
            return AgentConstants.DEFAULT_CONVERSATION_TITLE;
        }

        String title = content.length() > 30 ? content.substring(0, 30) : content;
        return title.trim();
    }
    
    /**
     * 从数据库加载历史对话消息
     * 
     * @param context Agent上下文
     * @param conversationId 会话ID
     */
    private void loadHistoryMessages(AgentContext context, String conversationId) {
        try {
            // 1. 获取历史消息加载数量配置（从 ThinkingConfig 中获取）
            int limit = context.getThinkingConfig() != null 
                ? context.getThinkingConfig().getHistoryMessageLoadLimitOrDefault() 
                : 20;
            
            // 2. 从数据库查询历史消息（数据库返回倒序，即最新的消息在前）
            List<MessageEntity> historyEntities = messageMapper.selectByConversationId(
                conversationId, 
                limit
            );
            
            if (historyEntities == null || historyEntities.isEmpty()) {
                log.debug("会话 {} 没有历史消息", conversationId);
                return;
            }
            
            // 3. 转换为 ChatMessage 列表
            List<ChatMessage> historyMessages = new ArrayList<>();
            for (MessageEntity entity : historyEntities) {
                ChatMessage message = convertToChatMessage(entity);
                if (message != null) {
                    historyMessages.add(message);
                }
            }
            
            // 4. 反转列表（因为数据库返回的是倒序，需要转为正序）
            Collections.reverse(historyMessages);
            
            // 5. 设置到 context
            context.setMessages(historyMessages);
            
            log.info("加载历史对话消息成功，会话: {}, 消息数: {}", 
                conversationId, historyMessages.size());
                
        } catch (Exception e) {
            log.error("加载历史对话消息失败，会话: {}", conversationId, e);
            // 失败不影响主流程，使用空消息列表
            if (context.getMessages() == null) {
                context.setMessages(new ArrayList<>());
            }
        }
    }
    
    /**
     * 将 MessageEntity 转换为 ChatMessage
     * 
     * @param entity 消息实体
     * @return ChatMessage
     */
    private ChatMessage convertToChatMessage(MessageEntity entity) {
        if (entity == null || entity.getContent() == null) {
            return null;
        }
        
        // 根据角色类型转换
        String role = entity.getRole();
        if ("user".equalsIgnoreCase(role)) {
            return new UserMessage(entity.getContent());
        } else if ("assistant".equalsIgnoreCase(role) || "ai".equalsIgnoreCase(role)) {
            return new AiMessage(entity.getContent());
        } else if ("system".equalsIgnoreCase(role)) {
            return new SystemMessage(entity.getContent());
        } else {
            log.warn("未知的消息角色类型: {}", role);
            return null;
        }
    }
}

