package com.aiagent.domain.model.bo;

import com.aiagent.api.dto.PersonalMcpToolSchema;
import com.aiagent.api.dto.RAGConfig;
import com.aiagent.application.AgentEventPublisher;
import com.aiagent.domain.action.ActionResult;
import com.aiagent.common.enums.AgentMode;
import com.aiagent.application.StreamingCallback;
import com.aiagent.domain.tool.todo.TodoItem;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.langchain4j.data.message.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 执行上下文
 * <p>
 * 职责：维护 Agent 执行过程中的**状态与历史**，以及运行时 I/O 通道。
 * 所有运行时配置（模型、模式、工具、RAG、上下文行为参数）统一聚合在
 * {@link AgentRuntimeConfig config} 字段中，由
 * {@link com.aiagent.domain.context.AgentContextService} 在请求初始化阶段构建。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── 会话标识 ──────────────────────────────────────────────────────────────

    /** 会话 ID */
    private String conversationId;

    /** 绑定的 Agent 定义 ID */
    private String agentId;

    // ── 运行时配置（本次请求生效，每次请求重新构建）────────────────────────────

    /**
     * 运行时配置（模型/模式/工具/知识库/RAG/上下文行为参数）。
     * 由 AgentContextService 在 loadOrCreateContext 中构建，序列化到 Redis。
     */
    @Builder.Default
    private AgentRuntimeConfig config = new AgentRuntimeConfig();

    // ── 运行时状态（随会话持久化到 Redis）─────────────────────────────────────

    /**
     * 对话历史（存储 BO 格式，用于 Redis 序列化）
     */
    private List<MessageBO> messageBOS;

    /**
     * 动作执行历史（按 ReACT 迭代轮次组织）
     * 外层 List：轮次索引；内层 List：该轮所有 ActionResult
     */
    private List<List<ActionResult>> actionExecutionHistory;

    /** RAG 检索历史 */
    private List<Map<String, Object>> ragRetrieveHistory;

    /** 迭代次数 */
    private Integer iterations;

    /**
     * Todo 任务清单（与会话绑定，随 AgentContext 持久化到 Redis）
     */
    @Builder.Default
    private List<TodoItem> todos = new ArrayList<>();

    // ── 运行时 I/O（transient，不序列化）──────────────────────────────────────

    /** 用户名 */
    private String username;

    /** 请求 ID（关联 SSE 事件） */
    @JsonIgnore
    private transient String requestId;

    /** 流式输出回调 */
    @JsonIgnore
    private transient StreamingCallback streamingCallback;

    /**
     * 事件发布器（向前端发送进度事件）
     */
    @JsonIgnore
    private transient AgentEventPublisher eventPublisher;

    /** 初始 RAG 检索结果（仅第一次请求使用） */
    @JsonIgnore
    private transient AgentKnowledgeResult initialRagResult;

    /**
     * 渐进式工具加载：本轮 LLM 通过 system_resolve_tools 请求加载的 MCP 工具名称集合
     */
    @JsonIgnore
    private transient Set<String> activeMcpToolNames = new HashSet<>();

    /**
     * 本次推理执行过程记录，由 FunctionCallingEngine 写入，AgentServiceImpl 读取后持久化。
     */
    @JsonIgnore
    private transient ExecutionProcessRecord executionProcess;

    // ── 消息操作 ─────────────────────────────────────────────────────────────

    /**
     * 从 ChatMessage 列表设置消息（自动转换为 BO）
     */
    @JsonIgnore
    public void setMessages(List<ChatMessage> messages) {
        if (messages == null) {
            this.messageBOS = new ArrayList<>();
        } else {
            this.messageBOS = messages.stream()
                .map(MessageBO::from)
                .collect(Collectors.toList());
        }
    }

    /**
     * 获取 ChatMessage 列表（从 BO 转换）
     */
    @JsonIgnore
    public List<ChatMessage> getMessages() {
        if (messageBOS == null) {
            return new ArrayList<>();
        }
        return messageBOS.stream()
            .map(MessageBO::toChatMessage)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /** 追加单条消息 */
    public void addMessage(ChatMessage message) {
        if (messageBOS == null) {
            messageBOS = new ArrayList<>();
        }
        MessageBO bo = MessageBO.from(message);
        if (bo != null) {
            messageBOS.add(bo);
        }
    }

    // ── 配置委托（从 config 透传，保持旧调用点无感知）─────────────────────────

    @JsonIgnore
    public String getModelId() {
        return config != null ? config.getModelId() : null;
    }

    @JsonIgnore
    public void setModelId(String modelId) {
        ensureConfig().setModelId(modelId);
    }

    @JsonIgnore
    public AgentMode getMode() {
        return config != null ? config.getMode() : AgentMode.AUTO;
    }

    @JsonIgnore
    public void setMode(AgentMode mode) {
        ensureConfig().setMode(mode);
    }
    @JsonIgnore
    public List<String> getEnabledTools() {
        return config != null ? config.getEnabledTools() : null;
    }

    @JsonIgnore
    public void setEnabledTools(List<String> tools) {
        ensureConfig().setEnabledTools(tools);
    }

    @JsonIgnore
    public List<String> getServerMcpIds() {
        return config != null ? config.getServerMcpIds() : null;
    }

    @JsonIgnore
    public void setServerMcpIds(List<String> ids) {
        ensureConfig().setServerMcpIds(ids);
    }

    @JsonIgnore
    public List<String> getPersonalMcpCapabilities() {
        return config != null ? config.getPersonalMcpCapabilities() : null;
    }

    @JsonIgnore
    public void setPersonalMcpCapabilities(List<String> capabilities) {
        ensureConfig().setPersonalMcpCapabilities(capabilities);
    }

    @JsonIgnore
    public List<PersonalMcpToolSchema> getPersonalMcpTools() {
        return config != null ? config.getPersonalMcpTools() : null;
    }

    @JsonIgnore
    public void setPersonalMcpTools(List<PersonalMcpToolSchema> tools) {
        ensureConfig().setPersonalMcpTools(tools);
    }

    @JsonIgnore
    public List<String> getKnowledgeIds() {
        return config != null ? config.getKnowledgeIds() : null;
    }

    @JsonIgnore
    public void setKnowledgeIds(List<String> ids) {
        ensureConfig().setKnowledgeIds(ids);
    }

    @JsonIgnore
    public Map<String, KnowledgeBase> getKnowledgeBaseMap() {
        return config != null ? config.getKnowledgeBaseMap() : null;
    }

    @JsonIgnore
    public void setKnowledgeBaseMap(Map<String, KnowledgeBase> map) {
        ensureConfig().setKnowledgeBaseMap(map);
    }

    @JsonIgnore
    public int getHistoryMessageLoadLimit() {
        return config != null ? config.getHistoryMessageLoadLimit() : 20;
    }

    @JsonIgnore
    public void setHistoryMessageLoadLimit(int limit) {
        ensureConfig().setHistoryMessageLoadLimit(limit);
    }

    @JsonIgnore
    public int getMaxToolRounds() {
        return config != null ? config.getMaxToolRounds() : 8;
    }

    @JsonIgnore
    public void setMaxToolRounds(int rounds) {
        ensureConfig().setMaxToolRounds(rounds);
    }

    @JsonIgnore
    public RAGConfig getRagConfig() {
        return config != null ? config.getRagConfig() : null;
    }

    @JsonIgnore
    public void setRagConfig(RAGConfig ragConfig) {
        ensureConfig().setRagConfig(ragConfig);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private AgentRuntimeConfig ensureConfig() {
        if (config == null) {
            config = new AgentRuntimeConfig();
        }
        return config;
    }
}
