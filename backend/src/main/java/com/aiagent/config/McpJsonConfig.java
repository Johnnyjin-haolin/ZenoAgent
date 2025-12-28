package com.aiagent.config;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP JSON配置文件结构
 * 对应 mcp.json 文件的格式
 * 
 * @author aiagent
 */
@Data
public class McpJsonConfig {
    
    /**
     * MCP服务器配置
     * Key: 服务器ID
     * Value: 服务器配置
     */
    @JSONField(name = "mcpServers")
    private Map<String, McpServerJsonDefinition> mcpServers = new HashMap<>();
    
    /**
     * MCP服务器JSON定义
     */
    @Data
    public static class McpServerJsonDefinition {
        /**
         * 连接类型：
         * - streamableHttp: Streamable HTTP传输（推荐，用于远程MCP服务器）
         * - stdio: 标准输入输出传输（本地进程）
         * - websocket: WebSocket传输（未来支持）
         */
        private String type;
        
        /**
         * 服务器URL（type=streamableHttp时使用）
         */
        private String url;
        
        /**
         * 命令（type=stdio时使用，可以是字符串或数组）
         */
        private Object command;
        
        /**
         * HTTP请求头（type=streamableHttp时使用）
         */
        private Map<String, String> headers;
        
        /**
         * 服务器显示名称
         */
        private String name;
        
        /**
         * 服务器描述
         */
        private String description;
        
        /**
         * 分组名称（前端按此分组展示）
         */
        private String group;
        
        /**
         * 是否启用（默认true）
         */
        private Boolean enabled;
        
        /**
         * 连接超时时间（毫秒），默认10秒
         */
        private Integer timeout;
        
        /**
         * 读取超时时间（毫秒），默认30秒
         */
        private Integer readTimeout;
        
        /**
         * 重试次数，默认3次
         */
        private Integer retryCount;
        
        /**
         * 重试间隔（毫秒），默认1秒
         */
        private Long retryInterval;
        
        /**
         * 是否启用缓存，默认true
         */
        private Boolean cacheEnabled;
        
        /**
         * 缓存时间（秒），默认5分钟
         */
        private Long cacheTtl;
    }
}

