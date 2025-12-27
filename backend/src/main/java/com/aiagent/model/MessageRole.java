package com.aiagent.model;

/**
 * 消息角色枚举
 * 
 * @author aiagent
 */
public enum MessageRole {
    
    USER("user", "用户"),
    ASSISTANT("assistant", "助手"),
    SYSTEM("system", "系统");
    
    private final String code;
    private final String name;
    
    MessageRole(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public static MessageRole fromCode(String code) {
        if (code == null) {
            return USER;
        }
        
        for (MessageRole role : MessageRole.values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        
        return USER;
    }
}


