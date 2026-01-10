package com.aiagent.enums;

/**
 * 模型类型枚举
 * 
 * @author aiagent
 */
public enum ModelType {
    /**
     * 对话模型（Chat Model）
     */
    CHAT("CHAT", "对话模型"),
    
    /**
     * 向量模型（Embedding Model）
     */
    EMBEDDING("EMBEDDING", "向量模型");
    
    private final String code;
    private final String description;
    
    ModelType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据代码获取枚举
     */
    public static ModelType fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return CHAT; // 默认返回 CHAT（向后兼容）
        }
        
        for (ModelType type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        
        // 如果未找到，默认返回 CHAT
        return CHAT;
    }
    
    /**
     * 根据代码判断是否有效
     */
    public static boolean isValid(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        
        for (ModelType type : values()) {
            if (type.code.equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        
        return false;
    }
}
