package com.aiagent.domain.skill;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Skill 领域对象
 * <p>
 * 每条 Skill 包含：
 * <ul>
 *   <li>摘要（summary）- 注入 System Prompt，引导 LLM 按需加载</li>
 *   <li>全文（content）- LLM 调用 system_load_skill 后才加载</li>
 *   <li>标签（tags）   - 用于在 Agent 配置页筛选</li>
 * </ul>
 */
@Data
public class AgentSkill {

    private String id;
    private String name;

    /** 摘要：一行描述，注入 System Prompt */
    private String summary;

    /** 全文：LLM 按需加载的完整 Skill 内容 */
    private String content;

    /** 标签列表，如 ["代码", "SQL"] */
    private List<String> tags = new ArrayList<>();

    /** 状态：active / deleted */
    private String status;

    private Date createTime;
    private Date updateTime;
}
