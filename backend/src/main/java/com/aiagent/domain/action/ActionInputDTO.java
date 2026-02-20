package com.aiagent.domain.action;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Action输入DTO
 * 用于接收LLM返回的JSON格式的action定义
 * 
 * @author aiagent
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionInputDTO {
    
    /**
     * 动作类型
     */
    private String actionType;
    
    /**
     * 动作名称
     */
    private String actionName;
    
    /**
     * 推理过程（为什么选择这个动作）
     */
    private String reasoning;
    
    /**
     * 工具调用参数（当actionType为TOOL_CALL时使用）
     */
    private ToolCallParams toolCallParams;
    
    /**
     * RAG检索参数（当actionType为RAG_RETRIEVE时使用）
     */
    private RAGRetrieveParams ragRetrieveParams;
    
    /**
     * LLM生成参数（当actionType为LLM_GENERATE时使用）
     */
    private LLMGenerateParams llmGenerateParams;
    
    /**
     * 直接返回响应参数（当actionType为DIRECT_RESPONSE时使用）
     */
    private DirectResponseParams directResponseParams;
}

