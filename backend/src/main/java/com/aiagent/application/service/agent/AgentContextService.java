package com.aiagent.application.service.agent;

import com.aiagent.shared.constant.AgentConstants;
import com.aiagent.application.service.memory.MemorySystem;
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
    private ConversationService conversationService;

    @Autowired
    private com.aiagent.application.service.rag.RAGEnhancer ragEnhancer;
    
    @Autowired
    private com.aiagent.application.service.rag.KnowledgeBaseService knowledgeBaseService;
    
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
            // 批量加载知识库信息并存储到 context
            if (!request.getKnowledgeIds().isEmpty()) {
                Map<String, com.aiagent.domain.model.KnowledgeBase> knowledgeBaseMap = 
                    knowledgeBaseService.getKnowledgeBasesByIds(request.getKnowledgeIds());
                context.setKnowledgeBaseMap(knowledgeBaseMap);
                log.debug("批量加载知识库信息: count={}", knowledgeBaseMap.size());
            }
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
        
        // 设置RAG配置（如果前端传入）
        if (request.getRagConfig() != null) {
            context.setRagConfig(request.getRagConfig());
            log.debug("使用前端传入的RAG配置: maxResults={}, minScore={}", 
                request.getRagConfig().getMaxResults(), 
                request.getRagConfig().getMinScore());
        } else {
            // 如果前端未传入，使用默认配置
            context.setRagConfig(com.aiagent.api.dto.RAGConfig.builder().build());
            log.debug("使用默认RAG配置");
        }
        
        // 强制从数据库加载最新的历史对话消息，覆盖Redis中的缓存（确保消息列表与ThinkingConfig配置一致）
        loadHistoryMessages(context, conversationId);

        return context;
    }

    public void ensureConversationExists(String conversationId, AgentRequest request) {
        try {
            ConversationInfo existingConversation = conversationService.getConversation(conversationId);

            if (existingConversation == null) {
                // 如果MySQL中不存在，创建新的会话
                ConversationInfo conversationInfo = ConversationInfo.builder()
                    .id(conversationId)
                    .title(generateTitle(request.getContent()))
                    .status(AgentConstants.DEFAULT_CONVERSATION_STATUS)
                    .messageCount(0)
                    .build();

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

            AgentKnowledgeResult ragResult = ragEnhancer.retrieve(query, context.getKnowledgeBaseMap(),context.getRagConfig());

            if (ragResult != null && ragResult.isNotEmpty()) {
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
            int limit = context.getThinkingConfig().getHistoryMessageLoadLimitOrDefault();
            
            // 2. 从数据库查询历史消息
            List<MessageEntity> historyEntities = messageMapper.selectByConversationId(
                conversationId, 
                limit
            );
            
            if (historyEntities == null || historyEntities.isEmpty()) {
                log.debug("会话 {} 没有历史消息", conversationId);
                context.setMessages(new ArrayList<>());
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
            // 4. 设置到 context
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

