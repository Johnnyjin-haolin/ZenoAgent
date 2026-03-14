package com.aiagent.domain.model.entity;

import lombok.Data;

import java.util.Date;

/**
 * Agent 定义实体类 - 对应数据库 agent_definition 表
 */
@Data
public class AgentDefinitionEntity {

    /** Agent ID（UUID 或 builtin-xxx） */
    private String id;

    /** Agent 名称 */
    private String name;

    /** Agent 描述 */
    private String description;

    /** 系统提示词 */
    private String systemPrompt;

    /**
     * 工具配置 JSON 字符串
     * 结构：{"mcpGroups":["group1"],"systemTools":["tool1"],"mcpTools":["mcpTool1"]}
     */
    private String toolsConfig;

    /** 是否内置（1=内置示例, 0=用户创建） */
    private Integer isBuiltin;

    /** 状态：active / deleted */
    private String status;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}
