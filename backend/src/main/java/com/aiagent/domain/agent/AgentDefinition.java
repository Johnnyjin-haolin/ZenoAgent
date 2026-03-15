package com.aiagent.domain.agent;

import com.aiagent.api.dto.RAGConfig;
import com.aiagent.domain.skill.SkillTreeNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 定义领域对象
 * <p>
 * 配置分四块：
 * <ul>
 *   <li>{@link ToolsConfig}   - 工具选择（服务端 MCP 服务器、系统工具、知识库）</li>
 *   <li>{@link ContextConfig} - 上下文行为（历史消息加载数、最大工具轮数）</li>
 *   <li>{@link RAGConfig}     - RAG 检索参数，直接复用 {@code api.dto.RAGConfig}</li>
 *   <li>skillTree             - Agent 私有 Skill 目录树</li>
 * </ul>
 */
@Data
public class AgentDefinition {

    private String id;
    private String name;
    private String description;
    private String systemPrompt;

    /** 工具选择配置 */
    private ToolsConfig tools = new ToolsConfig();

    /** 上下文行为配置（可选，不配置时使用引擎默认值） */
    private ContextConfig contextConfig;

    /** RAG 检索配置（可选，不配置时使用引擎默认值） */
    private RAGConfig ragConfig;

    /** Agent 私有 Skill 目录树（可选，节点引用全局 AgentSkill） */
    private List<SkillTreeNode> skillTree = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────

    /** 工具选择：Agent 可调用哪些工具 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolsConfig {

        /**
         * MCP 服务器工具选择列表（服务端执行，scope=0）
         * <p>
         * 每个元素指定一个 MCP 服务器及其可用工具：
         * <ul>
         *   <li>{@code toolNames == null} 或空列表：该服务器所有工具均可用</li>
         *   <li>{@code toolNames} 非空：仅允许列表中的工具名</li>
         * </ul>
         */
        private List<McpServerSelection> mcpServers = new ArrayList<>();

        /** 系统内置工具名称列表 */
        private List<String> systemTools = new ArrayList<>();

        /** 绑定的知识库 ID 列表 */
        private List<String> knowledgeIds = new ArrayList<>();
    }

    /** MCP 服务器工具细粒度选择 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class McpServerSelection {
        /** MCP 服务器 ID */
        private String serverId;
        /**
         * 允许的工具名称列表。
         * {@code null} 或空列表表示该服务器所有工具均可用；非空表示仅允许列表中的工具。
         */
        private List<String> toolNames;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 上下文行为配置
     * <p>控制 {@link com.aiagent.application.FunctionCallingEngine} 运行时的行为。
     */
    @Data
    public static class ContextConfig {
        /**
         * 从数据库加载的历史消息条数上限（默认 20）
         */
        private Integer historyMessageLoadLimit;

        /**
         * 最大工具调用轮数（默认 8）
         */
        private Integer maxToolRounds;
    }
}
