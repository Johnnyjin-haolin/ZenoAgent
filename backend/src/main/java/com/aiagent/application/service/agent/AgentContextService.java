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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
                .messageDTOs(new java.util.ArrayList<>())
                .toolCallHistory(new java.util.ArrayList<>())
                .ragRetrieveHistory(new java.util.ArrayList<>())
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
}

