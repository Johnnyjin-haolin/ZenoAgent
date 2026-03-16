package com.aiagent.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建/更新 Skill 的请求体
 */
@Data
public class AgentSkillRequest {

    /** Skill 名称（必填） */
    private String name;

    /** 摘要（必填，注入 System Prompt 的一行描述） */
    private String summary;

    /** Skill 全文内容（必填） */
    private String content;

    /** 标签列表（可选） */
    private List<String> tags;
}
