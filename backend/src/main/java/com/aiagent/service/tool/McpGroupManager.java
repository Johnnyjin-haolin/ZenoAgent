package com.aiagent.service.tool;

import com.aiagent.config.McpServerConfig;
import com.aiagent.vo.McpGroupInfo;
import com.aiagent.vo.McpToolInfo;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
            groupInfo.setConnectionType(server.getConnection().getType());
            groupInfo.setToolCount(0); // 稍后更新
            
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
            
            String connectionType = server.getConnection().getType();
            List<McpToolInfo> tools = new ArrayList<>();
            
            if ("local".equals(connectionType)) {
                // 本地工具（通过注解扫描，未来扩展）
                // TODO: 扫描@McpTool注解的工具
                tools.addAll(createPlaceholderTools(server));
            } else if (isSupportedTransportType(connectionType)) {
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
            } else {
                log.warn("不支持的连接类型: {}, serverId={}", connectionType, server.getId());
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
    private boolean isSupportedTransportType(String type) {
        return "streamable-http".equalsIgnoreCase(type) ||
               "http".equalsIgnoreCase(type) ||
               "stdio".equalsIgnoreCase(type) ||
               "remote".equalsIgnoreCase(type);
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
        toolInfo.setDescription(toolSpec.description() != null ? toolSpec.description() : "");
        toolInfo.setGroupId(server.getGroup());
        toolInfo.setEnabled(true);
        // 默认版本
        toolInfo.setVersion("1.0");
        toolInfo.setServerId(server.getId());
        toolInfo.setConnectionType(server.getConnection().getType());
        
        // 转换参数定义（JsonObjectSchema -> Map）
        // 这对于大模型理解如何调用工具非常重要
        if (toolSpec.parameters() != null) {
            try {
                Map<String, Object> parametersMap = convertParametersToMap(toolSpec.parameters());
                toolInfo.setParameters(parametersMap);
                log.debug("工具 {} 的参数定义已转换: {}", toolSpec.name(), parametersMap);
            } catch (Exception e) {
                log.warn("转换工具参数定义失败: toolName={}", toolSpec.name(), e);
                // 参数转换失败不影响工具注册，但大模型可能无法正确调用
            }
        } else {
            log.debug("工具 {} 没有参数定义", toolSpec.name());
        }
        
        return toolInfo;
    }
    
    /**
     * 将ToolSpecification的parameters转换为Map
     * parameters是JsonObjectSchema类型，包含工具参数的JSON Schema定义
     */
    private Map<String, Object> convertParametersToMap(Object parameters) {
        if (parameters == null) {
            return new HashMap<>();
        }
        
        try {
            // 方法1：尝试使用FastJSON2直接序列化
            // JsonObjectSchema应该可以被序列化为JSON
            String jsonString = JSON.toJSONString(parameters);
            JSONObject jsonObject = JSON.parseObject(jsonString);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = jsonObject.toJavaObject(Map.class);
            
            // 确保返回的是标准的JSON Schema格式
            if (result != null && !result.isEmpty()) {
                return result;
            }
        } catch (Exception e) {
            log.debug("使用FastJSON2序列化参数失败，尝试其他方式", e);
        }
        
        // 方法2：尝试使用反射获取properties和required
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("type", "object");
            
            // 尝试获取properties
            try {
                java.lang.reflect.Method propertiesMethod = parameters.getClass().getMethod("properties");
                Object properties = propertiesMethod.invoke(parameters);
                if (properties != null) {
                    // 将properties转换为Map
                    String propsJson = JSON.toJSONString(properties);
                    JSONObject propsObj = JSON.parseObject(propsJson);
                    result.put("properties", propsObj.toJavaObject(Map.class));
                } else {
                    result.put("properties", new HashMap<>());
                }
            } catch (NoSuchMethodException e) {
                result.put("properties", new HashMap<>());
            }
            
            // 尝试获取required
            try {
                java.lang.reflect.Method requiredMethod = parameters.getClass().getMethod("required");
                Object required = requiredMethod.invoke(parameters);
                if (required != null) {
                    result.put("required", required);
                }
            } catch (NoSuchMethodException e) {
                // required字段可选
            }
            
            return result;
        } catch (Exception e) {
            log.warn("使用反射提取参数定义失败", e);
        }
        
        // 方法3：返回默认结构
        Map<String, Object> defaultResult = new HashMap<>();
        defaultResult.put("type", "object");
        defaultResult.put("properties", new HashMap<>());
        return defaultResult;
    }
    
    /**
     * 创建占位工具（用于演示或本地工具）
     */
    private List<McpToolInfo> createPlaceholderTools(McpServerConfig.McpServerDefinition server) {
        List<McpToolInfo> tools = new ArrayList<>();
        
        // 根据分组名称创建示例工具
        String groupId = server.getGroup();
        
        if ("device".equals(groupId)) {
            tools.add(createTool("query_device_status", "查询设备状态", groupId, server.getId()));
            tools.add(createTool("control_device", "控制设备", groupId, server.getId()));
            tools.add(createTool("get_device_list", "获取设备列表", groupId, server.getId()));
        } else if ("analytics".equals(groupId)) {
            tools.add(createTool("analyze_data", "分析数据", groupId, server.getId()));
            tools.add(createTool("generate_report", "生成报表", groupId, server.getId()));
        } else if ("file".equals(groupId)) {
            tools.add(createTool("read_file", "读取文件", groupId, server.getId()));
            tools.add(createTool("write_file", "写入文件", groupId, server.getId()));
            tools.add(createTool("list_files", "列出文件", groupId, server.getId()));
        } else {
            // 默认工具
            tools.add(createTool("execute_task", "执行任务", groupId, server.getId()));
        }
        
        return tools;
    }
    
    /**
     * 创建工具信息
     */
    private McpToolInfo createTool(String name, String description, String groupId, String serverId) {
        McpToolInfo tool = new McpToolInfo();
        tool.setId(serverId + ":" + name); // 唯一ID
        tool.setName(name);
        tool.setDescription(description);
        tool.setGroupId(groupId);
        tool.setEnabled(true);
        tool.setVersion("1.0");
        tool.setServerId(serverId);
        tool.setConnectionType("local");
        return tool;
    }
    
    /**
     * 获取所有分组列表
     */
    public List<McpGroupInfo> getAllGroups() {
        return new ArrayList<>(groupCache.values());
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
     * 获取指定分组的工具
     */
    public List<McpToolInfo> getToolsByGroup(String groupId) {
        return toolsByGroup.getOrDefault(groupId, Collections.emptyList()).stream()
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
    
    /**
     * 检查分组是否存在
     */
    public boolean hasGroup(String groupId) {
        return groupCache.containsKey(groupId);
    }
    
    /**
     * 检查分组是否启用
     */
    public boolean isGroupEnabled(String groupId) {
        McpGroupInfo group = groupCache.get(groupId);
        return group != null && group.isEnabled();
    }
}


