package com.aiagent.api.dto;

import com.aiagent.domain.enums.ConnectionTypeEnums;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.Data;

import java.util.Map;

/**
 * MCP工具信息VO
 * 用于工具列表展示和前端展示
 * 
 * 注意：LangChain4j的McpToolProvider会自动处理工具注册和执行
 * 这里保留基本信息用于展示和分组管理，包括参数定义供大模型使用
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

    @JsonIgnore
    private JsonObjectSchema parameters;

    private  Map<String, Object> metadata;
    
    /**
     * 工具分组
     */
    private String groupId;
    
    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 所属服务器ID
     */
    private String serverId;
    
    /**
     * 连接类型（使用ConnectionType枚举）
     */
    private ConnectionTypeEnums connectionType;
}
