package com.aiagent.api.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Agent 定义 VO（接口返回给前端）
 */
@Data
public class AgentDefinitionVO {

    private String id;
    private String name;
    private String description;
    private String systemPrompt;
    private ToolsConfigVO tools;
    private ContextConfigVO contextConfig;
    private RagConfigVO ragConfig;
    private boolean builtin;
    private String status;
    private Date createTime;
    private Date updateTime;

    // ─────────────────────────────────────────────────────────────────────────

    @Data
    public static class ToolsConfigVO {
        private List<String> mcpGroups;
        private List<String> systemTools;
        /** 绑定的知识库 ID 列表 */
        private List<String> knowledgeIds;
    }

    @Data
    public static class ContextConfigVO {
        /** 历史消息加载条数上限 */
        private Integer historyMessageLoadLimit;
        /** 最大工具调用轮数 */
        private Integer maxToolRounds;
    }

    @Data
    public static class RagConfigVO {
        /** 最大检索文档数量 */
        private Integer maxResults;
        /** 最小相似度分数 */
        private Double minScore;
        /** 单文档最大字符数（null 表示不限制） */
        private Integer maxDocumentLength;
        /** 总内容最大字符数（null 表示不限制） */
        private Integer maxTotalContentLength;
    }
}
