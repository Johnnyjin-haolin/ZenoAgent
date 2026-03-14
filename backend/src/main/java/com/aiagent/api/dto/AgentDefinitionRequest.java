package com.aiagent.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建/更新 Agent 定义的请求体
 */
@Data
public class AgentDefinitionRequest {

    /** Agent 名称（必填） */
    private String name;

    /** Agent 描述 */
    private String description;

    /** 系统提示词 */
    private String systemPrompt;

    /** 工具选择配置 */
    private ToolsConfigRequest tools;

    /** 上下文行为配置（可选，不传则使用引擎默认值） */
    private ContextConfigRequest contextConfig;

    /** RAG 检索配置（可选，不传则使用引擎默认值） */
    private RagConfigRequest ragConfig;

    // ─────────────────────────────────────────────────────────────────────────

    @Data
    public static class ToolsConfigRequest {
        private List<String> mcpGroups;
        private List<String> systemTools;
        /** 绑定的知识库 ID 列表 */
        private List<String> knowledgeIds;
    }

    @Data
    public static class ContextConfigRequest {
        /** 历史消息加载条数上限（默认 20） */
        private Integer historyMessageLoadLimit;
        /** 最大工具调用轮数（默认 8） */
        private Integer maxToolRounds;
    }

    @Data
    public static class RagConfigRequest {
        /** 最大检索文档数量（默认 3） */
        private Integer maxResults;
        /** 最小相似度分数（默认 0.5） */
        private Double minScore;
        /** 单文档最大字符数（null 表示不限制） */
        private Integer maxDocumentLength;
        /** 总内容最大字符数（null 表示不限制） */
        private Integer maxTotalContentLength;
    }
}
