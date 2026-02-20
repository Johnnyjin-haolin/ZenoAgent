package com.aiagent.infrastructure.config;

import com.aiagent.common.enums.ConnectionTypeEnums;
import com.aiagent.common.util.StringUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP服务器配置类
 * 从JSON文件（config/mcp.json）读取MCP配置
 * 
 * @author aiagent
 */
@Slf4j
@Data
@Component
public class McpServerConfig {
    
    @Autowired
    private McpConfigLoader configLoader;
    
    /**
     * 是否启用MCP功能（可通过application.yml配置）
     */
    @Value("${aiagent.mcp.enabled:true}")
    private boolean enabled = true;
    
    /**
     * MCP服务器列表（从JSON文件加载）
     */
    private List<McpServerDefinition> servers = new ArrayList<>();
    
    @PostConstruct
    public void init() {
        loadFromJson();
        
        // 注册配置变更监听器，支持热加载
        configLoader.addConfigChangeListener(this::loadFromJson);
    }
    
    /**
     * 从JSON配置加载服务器列表
     */
    private void loadFromJson() {
        try {
            McpJsonConfig jsonConfig = configLoader.getCurrentConfig();
            
            if (jsonConfig == null || jsonConfig.getMcpServers() == null) {
                log.warn("JSON配置为空，使用空服务器列表");
                servers = new ArrayList<>();
                return;
            }
            
            // 将JSON配置转换为内部格式
            servers = jsonConfig.getMcpServers().entrySet().stream()
                .map(entry -> convertToServerDefinition(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
            
            log.info("从JSON配置加载了 {} 个MCP服务器", servers.size());
            
        } catch (Exception e) {
            log.error("从JSON配置加载服务器列表失败", e);
            servers = new ArrayList<>();
        }
    }
    
    /**
     * 将JSON配置转换为内部服务器定义
     */
    private McpServerDefinition convertToServerDefinition(
            String serverId, 
            McpJsonConfig.McpServerJsonDefinition jsonDef) {
        
        McpServerDefinition def = new McpServerDefinition();
        def.setId(serverId);
        def.setName(jsonDef.getName());
        def.setDescription(jsonDef.getDescription());
        def.setGroup(jsonDef.getGroup());
        def.setEnabled(jsonDef.getEnabled() != null ? jsonDef.getEnabled() : true);
        
        // 转换连接配置
        ConnectionConfig connection = new ConnectionConfig();
        
        // 转换类型：使用枚举解析，支持多种格式
        ConnectionTypeEnums connectionType = ConnectionTypeEnums.fromString(jsonDef.getType());
        connection.setType(connectionType);
        
        connection.setUrl(jsonDef.getUrl());
        
        // 处理命令（stdio类型）
        if (jsonDef.getCommand() != null) {
            if (jsonDef.getCommand() instanceof String) {
                connection.setUrl((String) jsonDef.getCommand());
            } else if (jsonDef.getCommand() instanceof List) {
                // 命令数组转换为字符串（用空格分隔）
                @SuppressWarnings("unchecked")
                List<String> cmdList = (List<String>) jsonDef.getCommand();
                connection.setUrl(String.join(" ", cmdList));
            }
        }
        
        // 处理headers：保存完整的headers Map，同时提取apiKey便于单独使用
        if (jsonDef.getHeaders() != null && !jsonDef.getHeaders().isEmpty()) {
            // 保存完整的headers Map
            connection.setHeaders(jsonDef.getHeaders());
            
            // 如果headers中有Authorization，提取token部分为apiKey（去掉"Bearer "前缀）
            // 这样既保留了完整的headers，也方便某些场景直接使用纯token
            String authHeader = jsonDef.getHeaders().get("Authorization");
            if (StringUtils.isNotEmpty(authHeader)) {
                // 移除 "Bearer " 前缀，只保留token
                if (authHeader.startsWith("Bearer ")) {
                    connection.setApiKey(authHeader.substring(7));
                } else if (authHeader.startsWith("Basic ")) {
                    // 移除 "Basic " 前缀
                    connection.setApiKey(authHeader.substring(6));
                } else {
                    // 如果不是标准格式，直接使用整个值
                    connection.setApiKey(authHeader);
                }
            }
        }
        
        // 设置超时和重试配置
        connection.setTimeout(jsonDef.getTimeout() != null ? jsonDef.getTimeout() : 10000);
        connection.setReadTimeout(jsonDef.getReadTimeout() != null ? jsonDef.getReadTimeout() : 30000);
        connection.setRetryCount(jsonDef.getRetryCount() != null ? jsonDef.getRetryCount() : 3);
        connection.setRetryInterval(jsonDef.getRetryInterval() != null ? jsonDef.getRetryInterval() : 1000);
        connection.setCacheEnabled(jsonDef.getCacheEnabled() != null ? jsonDef.getCacheEnabled() : true);
        connection.setCacheTtl(jsonDef.getCacheTtl() != null ? jsonDef.getCacheTtl() : 300);
        
        def.setConnection(connection);
        
        return def;
    }
    
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
    }
    
    /**
     * 连接配置
     */
    @Data
    public static class ConnectionConfig {
        /**
         * 连接类型
         * - STREAMABLE_HTTP: Streamable HTTP传输（推荐，用于远程MCP服务器）
         * - STDIO: 标准输入输出传输（本地进程）
         * - WEBSOCKET: WebSocket传输（未来支持）
         * - DOCKER: Docker传输（未来支持）
         */
        private ConnectionTypeEnums type = ConnectionTypeEnums.STDIO;
        
        /**
         * 服务器URL或命令：
         * - type=streamable-http时：POST端点URL（如 http://localhost:3001/mcp）
         * - type=stdio时：命令字符串（如 "node mcp-server.js" 或完整命令路径）
         * - type=websocket时：WebSocket URL（如 ws://localhost:3001/mcp/ws）
         */
        private String url;
        
        /**
         * API密钥（从headers中的Authorization提取，用于认证）
         */
        private String apiKey;
        
        /**
         * HTTP请求头（保留，用于扩展）
         */
        private Map<String, String> headers;
        
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
