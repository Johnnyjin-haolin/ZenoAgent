package com.aiagent.application.model;

import com.aiagent.domain.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent 执行计划
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlan {
    
    /**
     * 计划ID
     */
    private String planId;
    
    /**
     * 任务类型
     */
    private TaskType taskType;
    
    /**
     * 执行步骤列表
     */
    private List<AgentStep> steps;
    
    /**
     * 计划变量
     */
    private Map<String, Object> variables;
    
    /**
     * 预估执行时间（秒）
     */
    private Integer estimatedTime;
}


