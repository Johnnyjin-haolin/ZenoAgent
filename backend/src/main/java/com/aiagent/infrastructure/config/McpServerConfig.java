package com.aiagent.infrastructure.config;

import com.aiagent.common.enums.ConnectionTypeEnums;
import com.aiagent.common.enums.McpScope;
import com.aiagent.common.util.StringUtils;
import com.aiagent.domain.model.entity.McpServerEntity;
import com.aiagent.infrastructure.mapper.McpServerMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * MCP 服务器配置
 * <p>
 * 加载优先级：
 * 1. 数据库 mcp_server 表（GLOBAL 类型，可通过管理页面维护）
 * 2. mcp.json 文件（降级兜底，数据库有数据时跳过）
 * <p>
 * 注意：只有 GLOBAL（scope=0）类型的 MCP 服务器才需要在服务端加载客户端。
 * PERSONAL（scope=1）类型由浏览器客户端执行。
 */
@Slf4j
@Data
@Component
public class McpServerConfig {

    @Autowired
    private McpConfigLoader configLoader;

    /**
     * 注意：使用 @Lazy 避免与 McpServerRepository 的循环依赖
     */
    @Autowired
    @Lazy
    private McpServerMapper mcpServerMapper;

    @Value("${aiagent.mcp.enabled:true}")
    private boolean enabled = true;

    /**
     * GLOBAL MCP 服务器列表（内存缓存，供 McpClientFactory 使用）
     */
    private List<McpServerDefinition> servers = new CopyOnWriteArrayList<>();

    /**
     * 配置变更监听器列表
     */
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        reload();
        // mcp.json 变更时也触发 reload（降级场景）
        configLoader.addConfigChangeListener(this::reload);
    }

    /**
     * 重新加载配置
     * 优先从数据库，数据库无数据则降级到 mcp.json
     */
    public void reload() {
        List<McpServerDefinition> loaded = loadFromDatabase();
        if (!loaded.isEmpty()) {
            servers = new CopyOnWriteArrayList<>(loaded);
            log.info("从数据库加载 GLOBAL MCP 服务器: {} 个", servers.size());
        } else {
            loaded = loadFromJson();
            servers = new CopyOnWriteArrayList<>(loaded);
            log.info("数据库无 GLOBAL MCP 配置，降级使用 mcp.json: {} 个", servers.size());
        }
        notifyListeners();
    }

    /**
     * 从数据库加载 GLOBAL MCP 服务器（scope=0，enabled=1）
     */
    private List<McpServerDefinition> loadFromDatabase() {
        try {
            List<McpServerEntity> entities = mcpServerMapper.selectByScope(McpScope.GLOBAL.getValue());
            if (entities == null || entities.isEmpty()) {
                return new ArrayList<>();
            }
            return entities.stream()
                    .filter(e -> e.getEnabled() != null && e.getEnabled() == 1)
                    .map(this::entityToDefinition)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("从数据库加载 MCP 配置失败，降级到文件: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 从 mcp.json 加载（降级兜底，只加载 GLOBAL 类型）
     */
    private List<McpServerDefinition> loadFromJson() {
        try {
            McpJsonConfig jsonConfig = configLoader.getCurrentConfig();
            if (jsonConfig == null || jsonConfig.getMcpServers() == null) {
                return new ArrayList<>();
            }
            return jsonConfig.getMcpServers().entrySet().stream()
                    .filter(entry -> {
                        Integer scope = entry.getValue().getScope();
                        // scope 为 null 或 0 均视为 GLOBAL
                        return scope == null || scope == McpScope.GLOBAL.getValue();
                    })
                    .map(entry -> convertJsonToDefinition(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("从 mcp.json 加载配置失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 按 ID 查找（供 McpServerService 使用）
     */
    public McpServerDefinition findById(String id) {
        return servers.stream()
                .filter(s -> id.equals(s.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 注册配置变更监听器
     */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    /**
     * 兼容旧代码：getConfigLoader()
     */
    public McpConfigLoader getConfigLoader() {
        return configLoader;
    }

    private void notifyListeners() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.error("MCP 配置变更监听器执行失败", e);
            }
        }
    }

    // ── 转换方法 ──────────────────────────────────────────────────────────────

    private McpServerDefinition entityToDefinition(McpServerEntity entity) {
        McpServerDefinition def = new McpServerDefinition();
        def.setId(entity.getId());
        def.setName(entity.getName());
        def.setDescription(entity.getDescription());
        def.setEnabled(true);

        ConnectionConfig conn = new ConnectionConfig();
        conn.setType(ConnectionTypeEnums.fromString(entity.getConnectionType()));
        conn.setUrl(entity.getEndpointUrl());
        conn.setTimeout(entity.getTimeoutMs() != null ? entity.getTimeoutMs() : 10000);
        conn.setReadTimeout(entity.getReadTimeoutMs() != null ? entity.getReadTimeoutMs() : 30000);
        conn.setRetryCount(entity.getRetryCount() != null ? entity.getRetryCount() : 3);
        conn.setRetryInterval(1000);
        conn.setCacheEnabled(true);
        conn.setCacheTtl(300);

        // 认证信息：从 authHeader JSON 解析所有请求头键值对
        java.util.Map<String, String> authHeaders =
                com.aiagent.domain.mcp.McpServerService.parseAuthHeaders(entity.getAuthHeader());
        if (!authHeaders.isEmpty()) {
            conn.setHeaders(authHeaders);
            // 兼容 apiKey：取 Authorization 头的值（去掉 "Bearer " 前缀）
            String authValue = authHeaders.get("Authorization");
            if (StringUtils.isNotEmpty(authValue)) {
                conn.setApiKey(authValue.startsWith("Bearer ")
                        ? authValue.substring(7) : authValue);
            }
        }

        def.setConnection(conn);
        return def;
    }

    private McpServerDefinition convertJsonToDefinition(
            String serverId, McpJsonConfig.McpServerJsonDefinition jsonDef) {
        McpServerDefinition def = new McpServerDefinition();
        def.setId(serverId);
        def.setName(jsonDef.getName());
        def.setDescription(jsonDef.getDescription());
        def.setEnabled(jsonDef.getEnabled() != null ? jsonDef.getEnabled() : true);

        ConnectionConfig conn = new ConnectionConfig();
        conn.setType(ConnectionTypeEnums.fromString(jsonDef.getType()));
        conn.setUrl(jsonDef.getUrl());

        if (jsonDef.getCommand() != null) {
            if (jsonDef.getCommand() instanceof String) {
                conn.setUrl((String) jsonDef.getCommand());
            } else if (jsonDef.getCommand() instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> cmdList = (List<String>) jsonDef.getCommand();
                conn.setUrl(String.join(" ", cmdList));
            }
        }

        if (jsonDef.getHeaders() != null && !jsonDef.getHeaders().isEmpty()) {
            conn.setHeaders(jsonDef.getHeaders());
            String authHeader = jsonDef.getHeaders().get("Authorization");
            if (StringUtils.isNotEmpty(authHeader)) {
                if (authHeader.startsWith("Bearer ")) {
                    conn.setApiKey(authHeader.substring(7));
                } else {
                    conn.setApiKey(authHeader);
                }
            }
        }

        conn.setTimeout(jsonDef.getTimeout() != null ? jsonDef.getTimeout() : 10000);
        conn.setReadTimeout(jsonDef.getReadTimeout() != null ? jsonDef.getReadTimeout() : 30000);
        conn.setRetryCount(jsonDef.getRetryCount() != null ? jsonDef.getRetryCount() : 3);
        conn.setRetryInterval(jsonDef.getRetryInterval() != null ? jsonDef.getRetryInterval() : 1000);
        conn.setCacheEnabled(jsonDef.getCacheEnabled() != null ? jsonDef.getCacheEnabled() : true);
        conn.setCacheTtl(jsonDef.getCacheTtl() != null ? jsonDef.getCacheTtl() : 300);

        def.setConnection(conn);
        return def;
    }

    // ── 内部数据结构（保持不变，供 McpClientFactory 使用）──────────────────────

    @Data
    public static class McpServerDefinition {
        private String id;
        private String name;
        private String description;
        private boolean enabled = true;
        private ConnectionConfig connection = new ConnectionConfig();
    }

    @Data
    public static class ConnectionConfig {
        private ConnectionTypeEnums type = ConnectionTypeEnums.STDIO;
        private String url;
        private String apiKey;
        private Map<String, String> headers;
        private int timeout = 10000;
        private int readTimeout = 30000;
        private int retryCount = 3;
        private long retryInterval = 1000;
        private boolean cacheEnabled = true;
        private long cacheTtl = 300;
    }
}
