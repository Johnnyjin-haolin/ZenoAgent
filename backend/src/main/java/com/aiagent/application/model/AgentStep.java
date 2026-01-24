package com.aiagent.application.model;

import com.aiagent.domain.enums.StepType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 执行步骤
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStep {
    
    /**
     * 步骤ID
     */
    private String stepId;
    
    /**
     * 步骤序号
     */
    private Integer stepNumber;
    
    /**
     * 步骤类型
     */
    private StepType type;
    
    /**
     * 步骤描述
     */
    private String description;
    
    /**
     * 步骤参数
     */
    private Map<String, Object> params;
    
    /**
     * 下一步骤ID
     */
    private String nextStepId;
    
    /**
     * 步骤执行结果
     */
    private Object result;
    
    /**
     * 步骤执行状态
     */
    private String status;
}


