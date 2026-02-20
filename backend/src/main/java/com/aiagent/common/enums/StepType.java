package com.aiagent.common.enums;

/**
 * Agent 执行步骤类型
 * 
 * @author aiagent
 */
public enum StepType {
    
    /**
     * RAG检索
     */
    RAG_RETRIEVE,
    
    /**
     * 工具调用
     */
    TOOL_CALL,
    
    /**
     * LLM生成
     */
    LLM_GENERATE,
    
    /**
     * 工作流执行
     */
    WORKFLOW_RUN,
    
    /**
     * 条件判断
     */
    CONDITION
}


