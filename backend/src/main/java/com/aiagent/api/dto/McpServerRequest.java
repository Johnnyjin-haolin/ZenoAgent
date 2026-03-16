package com.aiagent.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 创建 / 更新 MCP 服务器请求体
 */
@Data
public class McpServerRequest {

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
     * GLOBAL 类型：{"Authorization":"Bearer sk-xxx","X-Tenant-Id":"t001"}
     * PERSONAL 类型：{"Authorization":"","X-Api-Key":""}（值留空，运行时由浏览器 localStorage 补充）
     * 传 null 或空 Map 表示无需认证
     */
    private Map<String, String> authHeaders;

    /**
     * 额外请求头（JSON 字符串）
     */
    private String extraHeaders;

    private Integer timeoutMs;
    private Integer readTimeoutMs;
    private Integer retryCount;
    private Boolean enabled;
}
