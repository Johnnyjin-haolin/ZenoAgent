package com.aiagent.model;

/**
 * MCP连接类型枚举
 * 
 * @author aiagent
 */
public enum ConnectionType {
    
    /**
     * Streamable HTTP传输（推荐，用于远程MCP服务器）
     */
    STREAMABLE_HTTP("streamable-http"),
    
    /**
     * 标准输入输出传输（本地进程）
     */
    STDIO("stdio"),
    
    /**
     * WebSocket传输（未来支持）
     */
    WEBSOCKET("websocket"),
    
    /**
     * Docker传输（未来支持）
     */
    DOCKER("docker");
    
    private final String value;
    
    ConnectionType(String value) {
        this.value = value;
    }
    
    /**
     * 获取枚举值
     */
    public String getValue() {
        return value;
    }
    
    /**
     * 从字符串解析连接类型
     * 支持多种格式：
     * - streamableHttp, streamable-http -> STREAMABLE_HTTP
     * - stdio -> STDIO
     * - websocket, ws -> WEBSOCKET
     * - docker -> DOCKER
     * 
     * @param typeStr 类型字符串
     * @return ConnectionType枚举，如果无法识别则返回STDIO（默认值）
     */
    public static ConnectionType fromString(String typeStr) {
        if (typeStr == null || typeStr.trim().isEmpty()) {
            return STDIO;
        }
        
        String normalized = typeStr.trim().toLowerCase();
        
        // 处理streamableHttp的多种格式
        if (normalized.equals("streamablehttp") || 
            normalized.equals("streamable-http") ||
            normalized.equals("streamable_http")) {
            return STREAMABLE_HTTP;
        }
        
        // 处理stdio
        if (normalized.equals("stdio")) {
            return STDIO;
        }
        
        // 处理websocket的多种格式
        if (normalized.equals("websocket") || 
            normalized.equals("ws") ||
            normalized.equals("web-socket")) {
            return WEBSOCKET;
        }
        
        // 处理docker
        if (normalized.equals("docker")) {
            return DOCKER;
        }
        
        // 默认返回STDIO
        return STDIO;
    }
    
    /**
     * 检查是否为HTTP类型的连接（需要URL）
     */
    public boolean requiresUrl() {
        return this == STREAMABLE_HTTP || this == WEBSOCKET;
    }
    
    /**
     * 检查是否为进程类型的连接（需要命令）
     */
    public boolean requiresCommand() {
        return this == STDIO || this == DOCKER;
    }
}

