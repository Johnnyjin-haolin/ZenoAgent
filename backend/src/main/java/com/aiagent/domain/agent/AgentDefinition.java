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
 *   <li>{@link ToolsConfig}   - 工具选择（服务端 MCP 服务器、个人 MCP 能力、系统工具、知识库）</li>
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
         * 绑定的 GLOBAL MCP 服务器 ID 列表（服务端执行，scope=0）
         * 对应 mcp_server 表中 scope=0 的记录
         */
        private List<String> serverMcpIds = new ArrayList<>();

        /**
         * 绑定的 PERSONAL MCP 能力标签列表（客户端执行，scope=1）
         * 如 ["github", "notion", "gmail"]
         * Agent 声明能力需求，具体 MCP 由当前用户的本地配置提供
         */
        private List<String> personalMcpCapabilities = new ArrayList<>();

        /** 系统内置工具名称列表 */
        private List<String> systemTools = new ArrayList<>();

        /** 绑定的知识库 ID 列表 */
        private List<String> knowledgeIds = new ArrayList<>();
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
