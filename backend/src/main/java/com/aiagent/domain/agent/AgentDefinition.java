package com.aiagent.domain.agent;

import com.aiagent.api.dto.RAGConfig;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 定义领域对象
 * <p>
 * 配置分三块：
 * <ul>
 *   <li>{@link ToolsConfig}   - 工具选择（MCP 分组、系统工具、知识库）</li>
 *   <li>{@link ContextConfig} - 上下文行为（历史消息加载数、最大工具轮数）</li>
 *   <li>{@link RAGConfig}     - RAG 检索参数，直接复用 {@code api.dto.RAGConfig}</li>
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

    // ─────────────────────────────────────────────────────────────────────────

    /** 工具选择：Agent 可调用哪些工具 */
    @Data
    public static class ToolsConfig {
        /** MCP 服务器分组 ID 列表 */
        private List<String> mcpGroups = new ArrayList<>();
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
