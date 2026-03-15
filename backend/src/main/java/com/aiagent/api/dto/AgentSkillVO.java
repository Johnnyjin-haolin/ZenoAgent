package com.aiagent.api.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Skill VO（接口返回给前端）
 */
@Data
public class AgentSkillVO {

    private String id;
    private String name;
    private String summary;
    private String content;
    private List<String> tags;
    private String status;
    private Date createTime;
    private Date updateTime;
}
