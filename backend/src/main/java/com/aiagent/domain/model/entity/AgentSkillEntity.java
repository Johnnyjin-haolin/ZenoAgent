package com.aiagent.domain.model.entity;

import lombok.Data;

import java.util.Date;

/**
 * Skill 定义实体类 - 对应数据库 agent_skill 表
 */
@Data
public class AgentSkillEntity {

    /** Skill ID（UUID） */
    private String id;

    /** Skill 名称 */
    private String name;

    /** 摘要（注入 System Prompt 的一行描述） */
    private String summary;

    /** Skill 全文（LLM 按需加载） */
    private String content;

    /**
     * 标签列表 JSON 字符串，如 ["代码","SQL"]
     */
    private String tags;

    /** 状态：active / deleted */
    private String status;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}
