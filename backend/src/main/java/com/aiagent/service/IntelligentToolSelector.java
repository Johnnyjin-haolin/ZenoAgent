package com.aiagent.service;

import com.aiagent.vo.McpToolInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能工具选择器
 * 根据任务需求智能选择最合适的工具
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class IntelligentToolSelector {
    
    @Autowired
    private McpGroupManager mcpGroupManager;
    
    /**
     * 获取可用工具名称列表
     */
    public List<String> getAvailableToolNames() {
        List<McpToolInfo> tools = mcpGroupManager.getAllTools();
        return tools.stream()
            .filter(McpToolInfo::isEnabled)
            .map(McpToolInfo::getName)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据任务需求选择工具
     * 
     * @param taskRequirement 任务需求描述
     * @param enabledGroups 启用的工具分组
     * @return 选中的工具列表
     */
    public List<McpToolInfo> selectTools(String taskRequirement, List<String> enabledGroups) {
        log.info("开始智能选择工具，任务需求: {}", taskRequirement);
        
        // 获取可用工具
        List<McpToolInfo> availableTools = mcpGroupManager.getToolsByGroups(enabledGroups);
        
        if (availableTools.isEmpty()) {
            log.warn("未找到可用工具");
            return availableTools;
        }
        
        // TODO: 使用LLM或规则匹配选择最合适的工具
        // 当前简化实现：返回所有可用工具，让LLM决定使用哪个
        
        log.info("找到 {} 个可用工具", availableTools.size());
        return availableTools;
    }
    
    /**
     * 根据工具名称获取工具信息
     */
    public McpToolInfo getToolByName(String toolName) {
        List<McpToolInfo> allTools = mcpGroupManager.getAllTools();
        return allTools.stream()
            .filter(tool -> toolName.equals(tool.getName()))
            .findFirst()
            .orElse(null);
    }
}

