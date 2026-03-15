package com.aiagent.api.dto;

import com.aiagent.domain.skill.SkillTreeNode;
import lombok.Data;

import java.util.ArrayList;
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
    /** Agent 私有 Skill 目录树 */
    private List<SkillTreeNode> skillTree = new ArrayList<>();
    private boolean builtin;
    private String status;
    private Date createTime;
    private Date updateTime;

    // ─────────────────────────────────────────────────────────────────────────

    @Data
    public static class ToolsConfigVO {
        /** GLOBAL MCP 服务器 ID 列表（服务端执行） */
        private List<String> serverMcpIds;
        /** PERSONAL MCP 能力标签列表（客户端执行） */
        private List<String> personalMcpCapabilities;
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
