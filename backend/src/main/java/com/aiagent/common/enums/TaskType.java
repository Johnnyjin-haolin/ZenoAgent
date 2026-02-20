package com.aiagent.common.enums;

/**
 * 任务类型枚举
 * 
 * @author aiagent
 */
public enum TaskType {
    
    /**
     * 简单对话：直接 LLM 回复
     */
    SIMPLE_CHAT("简单对话", "闲聊、问候、简单问答等"),
    
    /**
     * RAG查询：需要知识库检索
     */
    RAG_QUERY("知识查询", "从知识库检索信息回答问题"),
    
    /**
     * 工具调用：单一或多个MCP工具调用
     */
    TOOL_CALL("工具调用", "调用外部工具或执行设备操作"),
    
    /**
     * 复杂任务：多步骤工作流
     */
    COMPLEX_WORKFLOW("复杂任务", "需要多步骤、多工具协同完成");
    
    private final String name;
    private final String description;
    
    TaskType(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 从字符串转换为枚举
     */
    public static TaskType fromString(String type) {
        if (type == null) {
            return SIMPLE_CHAT;
        }
        
        for (TaskType taskType : TaskType.values()) {
            if (taskType.name().equalsIgnoreCase(type)) {
                return taskType;
            }
        }
        
        return SIMPLE_CHAT;
    }
}


