package com.aiagent.vo;

import lombok.Data;

/**
 * MCP工具信息VO
 * 用于工具列表展示和前端展示
 * 
 * 注意：LangChain4j的McpToolProvider会自动处理工具注册和执行
 * 这里只保留基本信息用于展示和分组管理
 * 
 * @author aiagent
 */
@Data
public class McpToolInfo {
    
    /**
     * 工具ID（唯一标识，格式：serverId:toolName）
     */
    private String id;
    
    /**
     * 工具名称（唯一标识）
     */
    private String name;
    
    /**
     * 工具描述
     */
    private String description;
    
    /**
     * 工具分组
     */
    private String groupId;
    
    /**
     * 是否启用
     */
    private boolean enabled;
    
    /**
     * 工具版本
     */
    private String version;
    
    /**
     * 所属服务器ID
     */
    private String serverId;
    
    /**
     * 连接类型（http/stdio/local）
     */
    private String connectionType;
}
