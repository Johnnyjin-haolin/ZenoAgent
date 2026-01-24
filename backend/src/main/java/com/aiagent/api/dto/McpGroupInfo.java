package com.aiagent.api.dto;

import lombok.Data;

/**
 * MCP分组信息VO
 * 用于前端展示和选择
 * 
 * @author aiagent
 */
@Data
public class McpGroupInfo {
    
    /**
     * 分组ID（使用group字段值）
     */
    private String id;
    
    /**
     * 分组名称
     */
    private String name;
    
    /**
     * 分组描述
     */
    private String description;
    
    /**
     * 是否启用
     */
    private boolean enabled;
    
    /**
     * 工具数量
     */
    private int toolCount;
    
    /**
     * 所属服务器ID
     */
    private String serverId;
    
    /**
     * 连接类型
     */
    private String connectionType;
}
