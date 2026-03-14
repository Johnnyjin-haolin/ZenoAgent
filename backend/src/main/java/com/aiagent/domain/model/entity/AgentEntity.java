package com.aiagent.domain.model.entity;

import lombok.Data;

import java.util.Date;

/**
 * Agent 定义实体类 - 对应数据库 agent 表
 */
@Data
public class AgentEntity {

    /** Agent ID（UUID 或 builtin-xxx） */
    private String id;

    /** Agent 名称 */
    private String name;

    /** Agent 描述 */
    private String description;

    /** 系统提示词 */
    private String systemPrompt;

    /**
     * 工具选择配置 JSON 字符串
     * 结构：{"mcpGroups":["group1"],"systemTools":["tool1"],"knowledgeIds":["kb1"]}
     */
    private String toolsConfig;

    /**
     * 上下文行为配置 JSON 字符串（可为 null，使用引擎默认值）
     * 结构：{"historyMessageLoadLimit":20,"maxToolRounds":8}
     */
    private String contextConfig;

    /**
     * RAG 检索配置 JSON 字符串（可为 null，使用引擎默认值）
     * 结构：{"maxResults":3,"minScore":0.5,"maxDocumentLength":1000,"maxTotalContentLength":3000}
     */
    private String ragConfig;

    /** 是否内置（1=内置示例, 0=用户创建） */
    private Integer isBuiltin;

    /** 状态：active / deleted */
    private String status;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}
