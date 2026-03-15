package com.aiagent.domain.context;

import com.aiagent.domain.agent.AgentDefinition;
import com.aiagent.domain.agent.AgentDefinitionLoader;
import com.aiagent.domain.model.bo.AgentRuntimeConfig;
import com.aiagent.domain.model.bo.KnowledgeBase;
import com.aiagent.domain.model.bo.MessageBO;
import com.aiagent.domain.rag.KnowledgeBaseService;
import com.aiagent.domain.rag.RAGEnhancer;
import com.aiagent.common.constant.AgentConstants;
import com.aiagent.domain.memory.MemorySystem;
import com.aiagent.common.util.StringUtils;
import com.aiagent.common.util.UUIDGenerator;
import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.api.dto.AgentEventData;
import com.aiagent.domain.model.bo.AgentKnowledgeResult;
import com.aiagent.api.dto.AgentRequest;
import com.aiagent.api.dto.ConversationInfo;
import com.aiagent.api.dto.RAGConfig;
import com.aiagent.domain.conversation.ConversationService;
import com.aiagent.domain.model.entity.MessageEntity;
import com.aiagent.infrastructure.mapper.MessageMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent 上下文相关逻辑
 * <p>
 * 配置优先级（高 → 低）：
 * <ol>
 *   <li>前端请求 {@link AgentRequest} 中携带的运行时参数</li>
 *   <li>{@link AgentDefinition} 中持久化的默认配置（contextConfig / ragConfig）</li>
 *   <li>{@link AgentRuntimeConfig} 字段硬编码默认值</li>
 * </ol>
 */
@Slf4j
@Service
public class AgentContextService {

    @Autowired
    private MemorySystem memorySystem;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private RAGEnhancer ragEnhancer;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private AgentDefinitionLoader agentDefinitionLoader;

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
            String resolvedAgentId = resolveAgentId(request.getAgentId(), conversationId);
            context = AgentContext.builder()
                .conversationId(conversationId)
                .agentId(resolvedAgentId)
                .messageBOS(new ArrayList<>())
                .actionExecutionHistory(new ArrayList<>())
                .ragRetrieveHistory(new ArrayList<>())
                .iterations(0)
                .build();
        } else if (StringUtils.isNotEmpty(request.getAgentId())) {
            context.setAgentId(request.getAgentId());
        }

        // ── 加载 AgentDefinition，从中读取持久化的默认配置 ─────────────────
        AgentDefinition agentDef = agentDefinitionLoader.getById(context.getAgentId());

        // ── 构建本次请求的运行时配置 ──────────────────────────────────────────
        AgentRuntimeConfig.AgentRuntimeConfigBuilder cfgBuilder = AgentRuntimeConfig.builder();

        // 模型：请求指定 > context 缓存（Redis 中的旧值）
        String modelId = StringUtils.isNotEmpty(request.getModelId())
            ? request.getModelId()
            : (context.getConfig() != null ? context.getConfig().getModelId() : null);
        cfgBuilder.modelId(modelId);

        // 执行模式
        cfgBuilder.mode(request.getMode() != null ? request.getMode()
            : (context.getConfig() != null && context.getConfig().getMode() != null
                ? context.getConfig().getMode()
                : com.aiagent.common.enums.AgentMode.AUTO));

        // MCP 服务器工具选择（请求覆盖，降级到 AgentDefinition 配置）
        if (request.getMcpServers() != null) {
            cfgBuilder.mcpServers(request.getMcpServers());
        } else if (context.getConfig() != null && context.getConfig().getMcpServers() != null) {
            cfgBuilder.mcpServers(context.getConfig().getMcpServers());
        } else if (agentDef != null && agentDef.getTools() != null) {
            cfgBuilder.mcpServers(agentDef.getTools().getMcpServers());
        }

        // 系统内置工具选择（请求覆盖，降级到 AgentDefinition 配置）
        if (request.getSystemTools() != null) {
            cfgBuilder.systemTools(request.getSystemTools());
        } else if (context.getConfig() != null && context.getConfig().getSystemTools() != null) {
            cfgBuilder.systemTools(context.getConfig().getSystemTools());
        } else if (agentDef != null && agentDef.getTools() != null) {
            cfgBuilder.systemTools(agentDef.getTools().getSystemTools());
        }

        List<String> knowledgeIds = request.getKnowledgeIds() != null
            ? request.getKnowledgeIds()
            : (context.getConfig() != null ? context.getConfig().getKnowledgeIds() : null);
        cfgBuilder.knowledgeIds(knowledgeIds);

        if (knowledgeIds != null && !knowledgeIds.isEmpty()) {
            Map<String, KnowledgeBase> knowledgeBaseMap =
                knowledgeBaseService.getKnowledgeBasesByIds(knowledgeIds);
            cfgBuilder.knowledgeBaseMap(knowledgeBaseMap);
            log.debug("批量加载知识库信息: count={}", knowledgeBaseMap.size());
        }

        // 上下文行为参数：AgentDefinition > 默认值
        cfgBuilder.historyMessageLoadLimit(resolveHistoryLoadLimit(agentDef));
        cfgBuilder.maxToolRounds(resolveMaxToolRounds(agentDef));

        // RAG 配置：AgentDefinition > 默认值（直接使用 RAGConfig，无需字段复制）
        RAGConfig ragConfig = resolveRagConfig(agentDef);
        cfgBuilder.ragConfig(ragConfig);
        log.debug("RAG 配置: maxResults={}, minScore={}", ragConfig.getMaxResults(), ragConfig.getMinScore());

        context.setConfig(cfgBuilder.build());

        // personalMcpTools 是 transient 字段，每次请求直接从前端上传的 schema 写入，不经 Redis
        if (request.getPersonalMcpTools() != null && !request.getPersonalMcpTools().isEmpty()) {
            context.setPersonalMcpTools(request.getPersonalMcpTools());
            log.debug("写入 PERSONAL MCP 工具 schema: {} 个", request.getPersonalMcpTools().size());
        }

        int historyLoadLimit = context.getConfig().getHistoryMessageLoadLimit();
        // 强制从数据库加载最新的历史对话消息
        loadHistoryMessages(context, conversationId, historyLoadLimit);

        return context;
    }

    public void ensureConversationExists(String conversationId, AgentRequest request) {
        try {
            ConversationInfo existingConversation = conversationService.getConversation(conversationId);

            if (existingConversation == null) {
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

            AgentKnowledgeResult ragResult = ragEnhancer.retrieve(query, context.getKnowledgeBaseMap(), context.getRagConfig());

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

    // ─────────────────────────────────────────────────────────────────────────
    // 私有：配置解析（优先级：AgentDefinition > 硬编码默认值）
    // ─────────────────────────────────────────────────────────────────────────

    private int resolveHistoryLoadLimit(AgentDefinition agentDef) {
        if (agentDef != null && agentDef.getContextConfig() != null
                && agentDef.getContextConfig().getHistoryMessageLoadLimit() != null) {
            return agentDef.getContextConfig().getHistoryMessageLoadLimit();
        }
        return 20;
    }

    private int resolveMaxToolRounds(AgentDefinition agentDef) {
        if (agentDef != null && agentDef.getContextConfig() != null
                && agentDef.getContextConfig().getMaxToolRounds() != null) {
            return agentDef.getContextConfig().getMaxToolRounds();
        }
        return 8;
    }

    /**
     * 从 AgentDefinition 解析 RAG 配置。
     * AgentDefinition.ragConfig 已直接使用 {@link RAGConfig}，无需字段复制。
     */
    private RAGConfig resolveRagConfig(AgentDefinition agentDef) {
        if (agentDef != null && agentDef.getRagConfig() != null) {
            return agentDef.getRagConfig();
        }
        return new RAGConfig();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String generateTitle(String content) {
        if (StringUtils.isEmpty(content)) {
            return AgentConstants.DEFAULT_CONVERSATION_TITLE;
        }
        String title = content.length() > 30 ? content.substring(0, 30) : content;
        return title.trim();
    }

    /**
     * 从数据库加载历史对话消息
     */
    private void loadHistoryMessages(AgentContext context, String conversationId, int limit) {
        try {
            List<MessageEntity> historyEntities = messageMapper.selectByConversationId(
                conversationId,
                limit
            );

            if (historyEntities == null || historyEntities.isEmpty()) {
                log.debug("会话 {} 没有历史消息", conversationId);
                context.setMessages(new ArrayList<>());
                return;
            }

            List<ChatMessage> historyMessages = new ArrayList<>();
            for (MessageEntity entity : historyEntities) {
                ChatMessage message = convertToChatMessage(entity);
                if (message != null) {
                    historyMessages.add(message);
                }
            }
            context.setMessages(historyMessages);

            log.info("加载历史对话消息成功，会话: {}, 消息数: {}",
                conversationId, historyMessages.size());

        } catch (Exception e) {
            log.error("加载历史对话消息失败，会话: {}", conversationId, e);
            if (context.getMessages() == null) {
                context.setMessages(new ArrayList<>());
            }
        }
    }

    /**
     * 将 MessageEntity 转换为 ChatMessage
     */
    private ChatMessage convertToChatMessage(MessageEntity entity) {
        if (entity == null) {
            return null;
        }

        if (entity.getMetadata() != null && !entity.getMetadata().isEmpty()) {
            try {
                JSONObject metadata = JSON.parseObject(entity.getMetadata());
                if (metadata != null && metadata.containsKey("messageData")) {
                    Object messageDataObj = metadata.get("messageData");
                    MessageBO dto = null;

                    if (messageDataObj instanceof JSONObject) {
                        dto = ((JSONObject) messageDataObj).toJavaObject(MessageBO.class);
                    } else if (messageDataObj instanceof String) {
                        dto = JSON.parseObject((String) messageDataObj, MessageBO.class);
                    }

                    if (dto != null) {
                        ChatMessage restored = dto.toChatMessage();
                        if (restored != null) {
                            return restored;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("从 metadata 还原消息失败，降级使用 role+content: conversationId={}, error={}",
                    entity.getConversationId(), e.getMessage());
            }
        }

        String role = entity.getRole();
        String content = entity.getContent() != null ? entity.getContent() : "";

        if ("user".equalsIgnoreCase(role)) {
            return new UserMessage(content);
        } else if ("assistant".equalsIgnoreCase(role) || "ai".equalsIgnoreCase(role)) {
            return content.isEmpty() ? null : AiMessage.from(content);
        } else if ("system".equalsIgnoreCase(role)) {
            return SystemMessage.from(content);
        } else if ("tool".equalsIgnoreCase(role)) {
            log.warn("无法还原 tool 类型消息（缺少 metadata.messageData），跳过: messageId={}",
                entity.getMessageId());
            return null;
        } else {
            log.warn("未知的消息角色类型: {}", role);
            return null;
        }
    }

    /**
     * 解析 agentId
     */
    private String resolveAgentId(String requestAgentId, String conversationId) {
        if (StringUtils.isNotEmpty(requestAgentId)) {
            return requestAgentId;
        }
        try {
            ConversationInfo conv = conversationService.getConversation(conversationId);
            if (conv != null && StringUtils.isNotEmpty(conv.getAgentId())) {
                log.debug("从会话绑定中获取 agentId: conversationId={}, agentId={}", conversationId, conv.getAgentId());
                return conv.getAgentId();
            }
        } catch (Exception e) {
            log.warn("读取会话 agentId 失败，使用默认值: conversationId={}", conversationId);
        }
        return AgentConstants.DEFAULT_AGENT_ID;
    }
}
