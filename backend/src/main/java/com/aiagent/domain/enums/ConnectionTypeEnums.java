package com.aiagent.domain.enums;

/**
 * MCP连接类型枚举
 * 
 * 符合MCP (Model Context Protocol) 标准规范
 * 参考：https://modelcontextprotocol.io/
 * 
 * 支持的传输类型：
 * - stdio: 标准输入输出（本地进程通信）
 * - streamable-http: Streamable HTTP（远程HTTP服务器，推荐）
 * - websocket: WebSocket（双向实时通信）
 * - sse: Server-Sent Events（单向服务器推送，可作为streamable-http的变体）
 * 
 * @author aiagent
 */
public enum ConnectionTypeEnums {
    
    /**
     * 标准输入输出传输（stdio）
     * 用于本地进程通信，通过标准输入输出流进行通信
     * 这是MCP最基础的传输方式
     */
    STDIO("stdio"),
    
    /**
     * Streamable HTTP传输（streamable-http）
     * 推荐用于远程MCP服务器
     * 支持流式响应，使用HTTP POST请求和Server-Sent Events (SSE)
     */
    STREAMABLE_HTTP("streamable-http"),
    
    /**
     * WebSocket传输（websocket）
     * 支持双向实时通信
     * 适用于需要实时双向数据交换的场景
     */
    WEBSOCKET("websocket"),
    
    /**
     * Server-Sent Events传输（sse）
     * 单向服务器推送，可作为streamable-http的变体
     * 适用于服务器主动推送数据的场景
     */
    SSE("sse");
    
    private final String value;
    
    ConnectionTypeEnums(String value) {
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
     * 支持多种格式和别名，兼容不同配置方式
     * 
     * 支持的格式：
     * - stdio, std-io -> STDIO
     * - streamableHttp, streamable-http, streamable_http, http, remote -> STREAMABLE_HTTP
     * - websocket, ws, web-socket -> WEBSOCKET
     * - sse, server-sent-events -> SSE
     * - docker -> DOCKER
     * 
     * @param typeStr 类型字符串
     * @return ConnectionType枚举，如果无法识别则返回STDIO（默认值）
     */
    public static ConnectionTypeEnums fromString(String typeStr) {
        if (typeStr == null || typeStr.trim().isEmpty()) {
            return STDIO;
        }
        
        String normalized = typeStr.trim().toLowerCase();
        
        // 处理stdio
        if (normalized.equals("stdio") || normalized.equals("std-io")) {
            return STDIO;
        }
        
        // 处理streamableHttp的多种格式（包括http和remote别名）
        if (normalized.equals("streamablehttp") || 
            normalized.equals("streamable-http") ||
            normalized.equals("streamable_http") ||
            normalized.equals("http") ||  // http作为streamable-http的别名
            normalized.equals("remote")) {  // remote作为streamable-http的别名
            return STREAMABLE_HTTP;
        }
        
        // 处理websocket的多种格式
        if (normalized.equals("websocket") || 
            normalized.equals("ws") ||
            normalized.equals("web-socket")) {
            return WEBSOCKET;
        }
        
        // 处理SSE
        if (normalized.equals("sse") || 
            normalized.equals("server-sent-events") ||
            normalized.equals("server_sent_events")) {
            return SSE;
        }
        
        // 默认返回STDIO
        return STDIO;
    }
    
    /**
     * 检查是否为HTTP类型的连接（需要URL配置）
     * 
     * @return 是否需要URL
     */
    public boolean requiresUrl() {
        return this == STREAMABLE_HTTP || 
               this == WEBSOCKET || 
               this == SSE;
    }
    

    /**
     * 检查是否为流式传输（支持流式响应）
     * 
     * @return 是否支持流式传输
     */
    public boolean supportsStreaming() {
        return this == STREAMABLE_HTTP || 
               this == WEBSOCKET || 
               this == SSE;
    }
    
    /**
     * 检查是否为双向通信（支持客户端和服务器双向发送消息）
     * 
     * @return 是否支持双向通信
     */
    public boolean supportsBidirectional() {
        return this == WEBSOCKET || this == STDIO;
    }
}

