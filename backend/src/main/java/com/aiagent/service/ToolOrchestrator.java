package com.aiagent.service;

import com.aiagent.vo.McpToolInfo;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aiagent.constant.AgentConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 工具编排器
 * 智能选择和调用MCP工具
 * 
 * 注意：此实现为简化版本
 * MCP工具功能已通过LangChain4j的McpToolProvider自动处理
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class ToolOrchestrator {
    
    @Autowired
    private McpGroupManager mcpGroupManager;
    
    /**
     * 智能选择工具
     * 
     * 根据用户意图和启用的MCP分组选择工具
     * 
     * @param userIntent 用户意图
     * @param modelId 模型ID（可选，用于LLM选择工具）
     * @param enabledGroups 启用的MCP分组列表（可选，为空则使用所有启用分组）
     * @return 工具列表
     */
    public List<Object> selectTools(String userIntent, String modelId, List<String> enabledGroups) {
        log.info("开始智能选择工具，用户意图: {}, 启用分组: {}", userIntent, enabledGroups);
        
        // 1. 获取启用的工具（根据分组筛选）
        List<McpToolInfo> availableTools = mcpGroupManager.getToolsByGroups(enabledGroups);
        
        if (availableTools.isEmpty()) {
            log.warn("未找到可用工具，启用分组: {}", enabledGroups);
            return Collections.emptyList();
        }
        
        log.info("找到 {} 个可用工具", availableTools.size());
        
        // 2. TODO: 使用LLM或规则匹配选择合适的工具
        // 当前简化实现：返回所有可用工具，实际应该根据用户意图筛选
        
        // 转换为Object列表（兼容现有代码）
        List<Object> tools = new ArrayList<>(availableTools);
        
        return tools;
    }
    
    /**
     * 智能选择工具（重载方法，兼容旧代码）
     */
    public List<Object> selectTools(String userIntent, String modelId) {
        return selectTools(userIntent, modelId, null);
    }
    
    /**
     * 执行工具调用
     * 
     * 注意：这是一个占位实现，需要集成实际的MCP工具调用器
     * 
     * @param tool 工具定义
     * @param params 参数
     * @return 执行结果
     */
    public Object executeTool(Object tool, Map<String, Object> params) {
        log.info("执行工具: {}, 参数: {}", tool, params);
        
        // TODO: 集成实际的MCP工具调用器
        log.warn("MCP工具执行功能尚未实现，返回空结果。需要集成MCP模块。");
        
        throw new RuntimeException("MCP工具功能尚未实现，需要集成MCP模块");
    }
    
    /**
     * 解析工具调用结果
     */
    public String parseToolResult(Object toolResult, String userIntent) {
        if (toolResult == null) {
            return "工具执行完成，但未返回结果。";
        }
        
        // 如果是JSON对象，格式化输出
        if (toolResult instanceof Map || toolResult instanceof List) {
            try {
                return JSON.toJSONString(toolResult);
            } catch (Exception e) {
                return toolResult.toString();
            }
        }
        
        return toolResult.toString();
    }
}


