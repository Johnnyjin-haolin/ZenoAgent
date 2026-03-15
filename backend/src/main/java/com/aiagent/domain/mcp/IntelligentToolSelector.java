package com.aiagent.domain.mcp;

import com.aiagent.api.dto.McpToolInfo;
import com.aiagent.domain.agent.AgentDefinition;
import com.aiagent.infrastructure.external.mcp.McpManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能工具选择器
 * 根据任务需求智能选择最合适的工具
 */
@Slf4j
@Component
public class IntelligentToolSelector {

    @Autowired
    private McpManager mcpManager;

    /**
     * 根据 {@link AgentDefinition.McpServerSelection} 列表选择工具（支持工具名细粒度白名单）
     *
     * @param taskRequirement 任务需求描述
     * @param selections      MCP 服务器工具选择列表（null/空 → 全部工具）
     * @return 选中的工具列表
     */
    public List<McpToolInfo> selectTools(String taskRequirement,
                                         List<AgentDefinition.McpServerSelection> selections) {
        log.info("开始智能选择工具，任务需求: {}", taskRequirement);
        List<McpToolInfo> availableTools = mcpManager.getToolsBySelections(selections);
        log.info("最终找到 {} 个可用工具", availableTools.size());
        return availableTools;
    }

    /**
     * 根据工具名称获取工具信息
     */
    public McpToolInfo getToolByName(String toolName) {
        return mcpManager.getAllTools().stream()
                .filter(tool -> toolName.equals(tool.getName()))
                .findFirst()
                .orElse(null);
    }
}
