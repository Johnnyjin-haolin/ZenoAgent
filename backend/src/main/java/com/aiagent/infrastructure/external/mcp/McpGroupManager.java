package com.aiagent.infrastructure.external.mcp;

import com.aiagent.infrastructure.config.McpServerConfig;
import com.aiagent.common.enums.ConnectionTypeEnums;
import com.aiagent.api.dto.McpGroupInfo;
import com.aiagent.api.dto.McpToolInfo;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP分组管理器
 * 管理MCP服务器分组和工具列表（仅用于展示，不参与工具注册）
 * 
 * 注意：工具注册和执行由LangChain4j的McpToolProvider自动处理
 * 这个类仅用于：
 * - 工具列表展示（前端API）
 * - 分组管理
 * - 工具信息查询
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class McpGroupManager {
    
    @Autowired
    private McpServerConfig mcpConfig;
    
    @Autowired
    private McpClientFactory mcpClientFactory;
    
    /**
     * 分组信息缓存（group -> McpGroupInfo）
     */
    private final Map<String, McpGroupInfo> groupCache = new ConcurrentHashMap<>();
    
    /**
     * 工具信息缓存（group -> List<McpToolInfo>）
     */
    private final Map<String, List<McpToolInfo>> toolsByGroup = new ConcurrentHashMap<>();
    
    /**
     * 服务器客户端映射（serverId -> McpClient）
     */
    private final Map<String, McpClient> serverClients = new ConcurrentHashMap<>();
    
    /**
     * 初始化：扫描并注册分组和工具列表
     */
    @PostConstruct
    public void init() {
        if (!mcpConfig.isEnabled()) {
            log.info("MCP功能已禁用，跳过初始化");
            return;
        }
        
        log.info("开始初始化MCP分组管理器...");
        
        // 扫描并注册分组
        scanAndRegisterGroups();
        
        // 扫描并注册工具列表（用于展示）
        scanAndRegisterTools();
        
        log.info("MCP分组管理器初始化完成，共 {} 个分组", groupCache.size());
        
        // 注册配置变更监听器，支持热加载
        mcpConfig.getConfigLoader().addConfigChangeListener(this::reload);
    }
    
    /**
     * 重新加载配置（用于热加载）
     */
    public void reload() {
        log.info("重新加载MCP配置...");
        
        // 清空缓存
        groupCache.clear();
        toolsByGroup.clear();
        serverClients.clear();
        
        // 重新扫描
        scanAndRegisterGroups();
        scanAndRegisterTools();
        
        log.info("MCP配置重新加载完成，共 {} 个分组", groupCache.size());
    }
    
    /**
     * 扫描并注册分组
     */
    private void scanAndRegisterGroups() {
        for (McpServerConfig.McpServerDefinition server : mcpConfig.getServers()) {
            if (!server.isEnabled()) {
                log.debug("跳过禁用的服务器: {}", server.getId());
                continue;
            }
            
            String groupId = server.getGroup();
            if (groupId == null || groupId.isEmpty()) {
                log.warn("服务器 {} 未配置分组名称，跳过", server.getId());
                continue;
            }
            
            // 创建分组信息
            McpGroupInfo groupInfo = new McpGroupInfo();
            groupInfo.setId(groupId);
            groupInfo.setName(server.getName());
            groupInfo.setDescription(server.getDescription());
            groupInfo.setEnabled(server.isEnabled());
            groupInfo.setServerId(server.getId());
            // 设置连接类型（转换为字符串，用于前端展示）
            groupInfo.setConnectionType(server.getConnection().getType() != null ? 
                server.getConnection().getType().getValue() : ConnectionTypeEnums.STDIO.getValue());
            groupInfo.setToolCount(0);
            
            groupCache.put(groupId, groupInfo);
            log.info("注册MCP分组: {} - {}", groupId, server.getName());
        }
    }
    
    /**
     * 扫描并注册工具列表（用于展示）
     * 从LangChain4j MCP客户端获取工具列表
     */
    private void scanAndRegisterTools() {
        for (McpServerConfig.McpServerDefinition server : mcpConfig.getServers()) {
            if (!server.isEnabled()) {
                continue;
            }
            
            String groupId = server.getGroup();
            if (groupId == null || groupId.isEmpty()) {
                continue;
            }
            
            ConnectionTypeEnums connectionType = server.getConnection().getType();
            if (connectionType == null) {
                connectionType = ConnectionTypeEnums.STDIO;
            }
            
            List<McpToolInfo> tools = new ArrayList<>();

            if (!isSupportedTransportType(connectionType)) {
                log.warn("不支持的连接类型: {}, serverId={}", connectionType, server.getId());
                continue;
            }
            // 注意：当前ConnectionType枚举中没有LOCAL类型
            // 如果需要本地工具，可以考虑添加LOCAL枚举值，或使用其他方式标识
            // 这里暂时跳过本地工具的处理
            // 使用LangChain4j MCP客户端获取工具列表（用于展示）
            try {
                // 创建或获取MCP客户端
                McpClient client = mcpClientFactory.getOrCreateClient(server);
                serverClients.put(server.getId(), client);

                // 从MCP客户端获取工具列表
                // 注意：这里仅用于展示，工具注册由McpToolProvider自动处理
                List<ToolSpecification> toolSpecs = client.listTools();

                // 转换为本地工具定义
                for (ToolSpecification toolSpec : toolSpecs) {
                    McpToolInfo toolInfo = convertToLocalTool(toolSpec, server);
                    tools.add(toolInfo);
                }

                log.info("成功从MCP服务器获取工具列表: serverId={}, 工具数量={}",
                    server.getId(), tools.size());

            } catch (Exception e) {
                log.error("从MCP服务器获取工具列表失败: serverId={}", server.getId(), e);
                // 失败时不添加工具，但不影响其他服务器
            }

            toolsByGroup.put(groupId, tools);
            
            // 更新分组的工具数量
            McpGroupInfo groupInfo = groupCache.get(groupId);
            if (groupInfo != null) {
                groupInfo.setToolCount(tools.size());
            }
            
            log.info("分组 {} 注册了 {} 个工具（用于展示）", groupId, tools.size());
        }
    }
    
    /**
     * 检查是否为支持的传输类型
     */
    /**
     * 检查连接类型是否支持MCP传输
     * 
     * @param connectionType 连接类型枚举
     * @return 是否支持
     */
    private boolean isSupportedTransportType(ConnectionTypeEnums connectionType) {
        if (connectionType == null) {
            return false;
        }
        
        // 支持所有MCP标准传输类型
        return connectionType == ConnectionTypeEnums.STREAMABLE_HTTP ||
               connectionType == ConnectionTypeEnums.STDIO ||
               connectionType == ConnectionTypeEnums.WEBSOCKET ||
               connectionType == ConnectionTypeEnums.SSE;
               // DOCKER暂时不支持
    }
    
    /**
     * 将LangChain4j的ToolSpecification转换为本地McpToolInfo
     */
    private McpToolInfo convertToLocalTool(ToolSpecification toolSpec,
                                          McpServerConfig.McpServerDefinition server) {
        McpToolInfo toolInfo = new McpToolInfo();
        // 唯一ID
        toolInfo.setId(server.getId() + ":" + toolSpec.name());
        toolInfo.setName(toolSpec.name());
        toolInfo.setDescription(toolSpec.description());
        toolInfo.setGroupId(server.getGroup());
        toolInfo.setEnabled(true);
        toolInfo.setServerId(server.getId());
        toolInfo.setConnectionType(server.getConnection().getType());
        toolInfo.setParameters(toolSpec.parameters());
        toolInfo.setMetadata(toolSpec.metadata());

        return toolInfo;
    }


    /**
     * 获取所有启用的分组
     */
    public List<McpGroupInfo> getEnabledGroups() {
        return groupCache.values().stream()
                .filter(McpGroupInfo::isEnabled)
                .collect(Collectors.toList());
    }
    
    /**
     * 根据分组ID获取分组信息
     */
    public McpGroupInfo getGroupById(String groupId) {
        return groupCache.get(groupId);
    }
    
    /**
     * 根据分组列表获取工具
     * 
     * @param groups 分组列表（为空或null则返回所有启用分组的工具）
     */
    public List<McpToolInfo> getToolsByGroups(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            // 返回所有启用分组的工具
            // 返回所有启用分组的工具
            return getEnabledGroups().stream()
                    .flatMap(group -> {
                        List<McpToolInfo> tools = toolsByGroup.getOrDefault(group.getId(), Collections.emptyList());
                        return tools.stream();
                    })
                    .filter(McpToolInfo::isEnabled)
                    .collect(Collectors.toList());
        }
        
        // 返回指定分组的工具
        return groups.stream()
                .filter(groupCache::containsKey)
                .flatMap(groupId -> {
                    McpGroupInfo group = groupCache.get(groupId);
                    if (group == null || !group.isEnabled()) {
                        return Collections.<McpToolInfo>emptyList().stream();
                    }
                    List<McpToolInfo> tools = toolsByGroup.getOrDefault(groupId, Collections.emptyList());
                    return tools.stream();
                })
                .filter(McpToolInfo::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有工具
     */
    public List<McpToolInfo> getAllTools() {
        return toolsByGroup.values().stream()
                .flatMap(List::stream)
                .filter(McpToolInfo::isEnabled)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取MCP客户端（用于直接调用）
     */
    public McpClient getMcpClient(String serverId) {
        return serverClients.get(serverId);
    }

}


