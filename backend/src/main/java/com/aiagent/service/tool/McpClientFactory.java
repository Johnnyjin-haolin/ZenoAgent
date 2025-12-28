package com.aiagent.service.tool;

import com.aiagent.config.McpServerConfig;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP客户端工厂
 * 负责创建和管理 LangChain4j MCP 客户端
 * 
 * 根据LangChain4j文档最佳实践：
 * - 为每个客户端设置唯一的key（推荐）
 * - 支持配置缓存选项
 * - 管理客户端生命周期
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class McpClientFactory {
    
    @Autowired
    private McpServerConfig mcpConfig;
    
    @Autowired
    private McpTransportFactory transportFactory;
    
    /**
     * MCP客户端缓存（serverId -> McpClient）
     */
    private final Map<String, McpClient> clientCache = new ConcurrentHashMap<>();
    
    /**
     * 为服务器创建MCP客户端
     * 
     * @param server 服务器配置
     * @return MCP客户端
     */
    public McpClient createClient(McpServerConfig.McpServerDefinition server) {
        String serverId = server.getId();
        
        // 检查缓存
        if (clientCache.containsKey(serverId)) {
            return clientCache.get(serverId);
        }
        
        log.info("创建MCP客户端: serverId={}, type={}", serverId, server.getConnection().getType());
        
        try {
            // 创建传输
            McpTransport transport = transportFactory.createTransport(server);
            
            // 创建客户端（根据文档，设置key是推荐的）
            DefaultMcpClient.Builder builder = DefaultMcpClient.builder()
                .key(serverId)  // 设置唯一的key（推荐，用于区分多个客户端）
                .transport(transport);
            
            // 配置缓存选项（可选）
            // 默认启用缓存，可以通过配置禁用
            if (server.getConnection().isCacheEnabled()) {
                builder.cacheToolList(true);
            } else {
                builder.cacheToolList(false);
            }
            
            McpClient client = builder.build();
            
            // 缓存客户端
            clientCache.put(serverId, client);
            
            log.info("MCP客户端创建成功: serverId={}, key={}", serverId, serverId);
            return client;
            
        } catch (Exception e) {
            log.error("创建MCP客户端失败: serverId={}", serverId, e);
            throw new RuntimeException("创建MCP客户端失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取所有客户端
     */
    public List<McpClient> getAllClients() {
        return new ArrayList<>(clientCache.values());
    }
    
    /**
     * 获取客户端（如果已创建）
     */
    public McpClient getClient(String serverId) {
        return clientCache.get(serverId);
    }
    
    /**
     * 获取或创建客户端
     */
    public McpClient getOrCreateClient(McpServerConfig.McpServerDefinition server) {
        String serverId = server.getId();
        McpClient client = clientCache.get(serverId);
        if (client == null) {
            client = createClient(server);
        }
        return client;
    }
    
    /**
     * 关闭客户端
     */
    public void closeClient(String serverId) {
        McpClient client = clientCache.remove(serverId);
        if (client != null) {
            try {
                client.close();
                log.info("MCP客户端已关闭: serverId={}", serverId);
            } catch (Exception e) {
                log.error("关闭MCP客户端失败: serverId={}", serverId, e);
            }
        }
    }
    
    /**
     * 关闭所有客户端
     */
    @PreDestroy
    public void closeAllClients() {
        log.info("关闭所有MCP客户端...");
        List<String> serverIds = new ArrayList<>(clientCache.keySet());
        for (String serverId : serverIds) {
            closeClient(serverId);
        }
    }
    
    /**
     * 清除工具列表缓存
     */
    public void evictToolListCache(String serverId) {
        McpClient client = clientCache.get(serverId);
        if (client instanceof DefaultMcpClient) {
            ((DefaultMcpClient) client).evictToolListCache();
            log.info("已清除工具列表缓存: serverId={}", serverId);
        }
    }
}
