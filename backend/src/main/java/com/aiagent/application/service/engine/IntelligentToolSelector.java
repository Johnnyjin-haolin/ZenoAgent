package com.aiagent.application.service.engine;

import com.aiagent.infrastructure.external.mcp.McpGroupManager;
import com.aiagent.api.dto.McpToolInfo;
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
     * 根据任务需求选择工具
     * 
     * @param taskRequirement 任务需求描述
     * @param enabledGroups 启用的工具分组
     * @param enabledTools 启用的工具名称列表（为空则允许所有工具）
     * @return 选中的工具列表
     */
    public List<McpToolInfo> selectTools(String taskRequirement, List<String> enabledGroups, List<String> enabledTools) {
        log.info("开始智能选择工具，任务需求: {}", taskRequirement);
        
        // 1. 先按分组获取工具
        List<McpToolInfo> availableTools = mcpGroupManager.getToolsByGroups(enabledGroups);
        
        if (availableTools.isEmpty()) {
            log.warn("未找到可用工具");
            return availableTools;
        }
        
        // 2. 如果指定了 enabledTools，再进行工具名称过滤
        if (enabledTools != null && !enabledTools.isEmpty()) {
            log.info("根据启用工具列表过滤，启用工具: {}", enabledTools);
            availableTools = availableTools.stream()
                .filter(tool -> enabledTools.contains(tool.getName()))
                .collect(Collectors.toList());
            log.info("过滤后剩余 {} 个可用工具", availableTools.size());
        } else {
            log.info("未指定启用工具列表，允许所有分组内的工具");
        }

        // 当前简化实现：返回所有可用工具，让LLM决定使用哪个
        log.info("最终找到 {} 个可用工具", availableTools.size());
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

