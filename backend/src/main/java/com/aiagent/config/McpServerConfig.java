package com.aiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP服务器配置类
 * 从application.yml读取MCP配置
 * 
 * @author aiagent
 */
@Data
@Component
@ConfigurationProperties(prefix = "aiagent.mcp")
public class McpServerConfig {
    
    /**
     * 是否启用MCP功能
     */
    private boolean enabled = true;
    
    /**
     * MCP服务器列表
     */
    private List<McpServerDefinition> servers = new ArrayList<>();
    
    /**
     * MCP服务器定义
     */
    @Data
    public static class McpServerDefinition {
        /**
         * 服务器唯一标识
         */
        private String id;
        
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
        private boolean enabled = true;
        
        /**
         * 连接配置
         */
        private ConnectionConfig connection = new ConnectionConfig();
        
        /**
         * 连接配置
         */
        @Data
        public static class ConnectionConfig {
            /**
             * 连接类型：
             * - streamable-http: Streamable HTTP传输（推荐，用于远程MCP服务器）
             * - stdio: 标准输入输出传输（本地进程）
             * - websocket: WebSocket传输（未来支持）
             * - docker: Docker传输（未来支持）
             * - http: 已废弃，使用 streamable-http
             * - local: 已废弃，使用 stdio
             * - remote: 已废弃，使用 streamable-http
             */
            private String type = "stdio";
            
            /**
             * 服务器URL或命令：
             * - type=streamable-http时：POST端点URL（如 http://localhost:3001/mcp）
             * - type=stdio时：命令字符串（如 "node mcp-server.js" 或完整命令路径）
             * - type=websocket时：WebSocket URL（如 ws://localhost:3001/mcp/ws）
             */
            private String url;
            
            /**
             * API密钥（type=http时使用，用于认证）
             */
            private String apiKey;
            
            /**
             * 连接超时时间（毫秒），默认10秒
             */
            private int timeout = 10000;
            
            /**
             * 读取超时时间（毫秒），默认30秒
             */
            private int readTimeout = 30000;
            
            /**
             * 重试次数，默认3次
             */
            private int retryCount = 3;
            
            /**
             * 重试间隔（毫秒），默认1秒
             */
            private long retryInterval = 1000;
            
            /**
             * 是否启用缓存，默认true
             */
            private boolean cacheEnabled = true;
            
            /**
             * 缓存时间（秒），默认5分钟
             */
            private long cacheTtl = 300;
        }
    }
}
