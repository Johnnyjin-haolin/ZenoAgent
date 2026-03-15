package com.aiagent.api.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务器信息 VO（响应给前端）
 */
@Data
public class McpServerVO {

    private String id;
    private String name;
    private String description;

    /**
     * 作用域：0=GLOBAL, 1=PERSONAL
     */
    private Integer scope;

    private String ownerUserId;

    private String connectionType;
    private String endpointUrl;

    /**
     * 认证请求头（键值对）
     * <p>
     * GLOBAL 类型：返回 Header 名，值脱敏为 "***"（不返回明文）
     * PERSONAL 类型：返回 Header 名，值为 ""（密钥状态由前端 localStorage 判断）
     */
    private Map<String, String> authHeaders;

    private Integer timeoutMs;
    private Integer readTimeoutMs;
    private Integer retryCount;
    private Boolean enabled;
    private String createdBy;
    private Date createdAt;
    private Date updatedAt;

    /**
     * 工具列表（按需加载，调用 /tools 接口时填充）
     */
    private List<McpToolInfo> tools;

    /**
     * 工具数量
     */
    private Integer toolCount;
}
