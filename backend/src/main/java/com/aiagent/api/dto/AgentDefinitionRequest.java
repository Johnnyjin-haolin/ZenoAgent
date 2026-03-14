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

    /** 工具配置 */
    private ToolsConfigRequest tools;

    @Data
    public static class ToolsConfigRequest {
        private List<String> mcpGroups;
        private List<String> systemTools;
        /** 绑定的知识库 ID 列表 */
        private List<String> knowledgeIds;
    }
}
