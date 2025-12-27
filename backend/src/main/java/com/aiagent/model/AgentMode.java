package com.aiagent.model;

/**
 * Agent执行模式
 * 
 * @author aiagent
 */
public enum AgentMode {
    
    /**
     * 自动模式：AI自主决策工具调用
     */
    AUTO("自动模式"),
    
    /**
     * 手动模式：需要用户确认后执行工具
     */
    MANUAL("手动模式");
    
    private final String name;
    
    AgentMode(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public static AgentMode fromString(String mode) {
        if (mode == null) {
            return AUTO;
        }
        
        for (AgentMode agentMode : AgentMode.values()) {
            if (agentMode.name().equalsIgnoreCase(mode)) {
                return agentMode;
            }
        }
        
        return AUTO;
    }
}


