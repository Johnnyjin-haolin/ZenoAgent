package com.aiagent.domain.model.bo;

import com.aiagent.api.dto.RAGConfig;
import com.aiagent.common.enums.AgentMode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Agent 运行时配置
 * <p>
 * 聚合本次请求所需的全部配置项，由 {@link com.aiagent.domain.context.AgentContextService}
 * 在 {@code loadOrCreateContext} 阶段统一构建，写入 {@link AgentContext#config}。
 * <p>
 * 配置优先级（高 → 低）：
 * <ol>
 *   <li>前端请求 {@link com.aiagent.api.dto.AgentRequest} 中的运行时参数</li>
 *   <li>{@link com.aiagent.domain.agent.AgentDefinition} 中持久化的默认配置</li>
 *   <li>硬编码默认值（本类字段默认值）</li>
 * </ol>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRuntimeConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── 模型 & 执行模式 ───────────────────────────────────────────────────────

    /** 使用的模型 ID */
    private String modelId;

    /** 执行模式（AUTO / MANUAL），默认 AUTO */
    @Builder.Default
    private AgentMode mode = AgentMode.AUTO;

    // ── 工具 & 知识库 ─────────────────────────────────────────────────────────

    /** 启用的 MCP 服务分组 ID 列表（空表示允许全部） */
    private List<String> enabledMcpGroups;

    /** 启用的工具名称列表（空表示允许全部） */
    private List<String> enabledTools;

    /** 关联知识库 ID 列表 */
    private List<String> knowledgeIds;

    /**
     * 知识库对象映射（knowledgeId → KnowledgeBase），在初始化时批量加载。
     * 不序列化：Redis 存储的是 AgentContext，此字段每次请求重新加载即可。
     */
    @JsonIgnore
    private transient Map<String, KnowledgeBase> knowledgeBaseMap;

    // ── 上下文行为（来自 AgentDefinition.contextConfig）─────────────────────

    /** 从数据库加载历史消息的条数上限（默认 20） */
    @Builder.Default
    private int historyMessageLoadLimit = 20;

    /** 最大工具调用轮数（默认 8） */
    @Builder.Default
    private int maxToolRounds = 8;

    // ── RAG 检索（来自 AgentDefinition.ragConfig）────────────────────────────

    /**
     * RAG 检索配置。
     * 直接使用 {@link RAGConfig}，不再有第二份领域内部类。
     */
    @JsonIgnore
    private transient RAGConfig ragConfig;
}
