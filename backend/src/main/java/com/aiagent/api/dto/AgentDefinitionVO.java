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
    private boolean builtin;
    private String status;
    private Date createTime;
    private Date updateTime;

    @Data
    public static class ToolsConfigVO {
        private List<String> mcpGroups;
        private List<String> systemTools;
        /** 绑定的知识库 ID 列表 */
        private List<String> knowledgeIds;
    }
}
