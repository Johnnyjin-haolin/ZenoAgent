package com.aiagent.domain.tool;

import com.aiagent.api.dto.McpToolInfo;
import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.agent.AgentDefinition;
import com.aiagent.infrastructure.config.AgentConfig;
import com.aiagent.infrastructure.external.mcp.McpGroupManager;
import com.aiagent.infrastructure.external.mcp.McpToolExecutor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 统一工具注册表
 * 合并系统内置工具（SystemTool）和 MCP 工具，对外提供统一的 resolveToolSpecifications 和 execute 接口
 *
 * @author aiagent
 */
@Slf4j
@Component
public class ToolRegistry {

    @Autowired
    private List<SystemTool> systemTools;

    @Autowired
    private McpGroupManager mcpGroupManager;

    @Autowired
    private McpToolExecutor mcpToolExecutor;

    @Autowired
    private AgentConfig agentConfig;

    /**
     * 根据 AgentDefinition 组装 ToolSpecification 列表，直接传给 ChatRequest.toolSpecifications()
     */
    public List<ToolSpecification> resolveToolSpecifications(AgentDefinition agentDef) {
        List<ToolSpecification> result = new ArrayList<>();

        if (agentDef == null || agentDef.getTools() == null) {
            return result;
        }

        // 1. 系统内置工具
        List<String> declaredSystemTools = agentDef.getTools().getSystemTools();
        if (declaredSystemTools != null) {
            for (String sysToolName : declaredSystemTools) {
                systemTools.stream()
                    .filter(t -> t.getName().equals(sysToolName) || t.getName().equals("system_" + sysToolName.toLowerCase().replace("_", "_")))
                    .findFirst()
                    .ifPresent(t -> {
                        result.add(t.getSpecification());
                        log.debug("注册系统工具: {}", t.getName());
                    });
            }
        }

        // 2. MCP 工具：McpToolInfo → ToolSpecification
        List<String> mcpGroups = agentDef.getTools().getMcpGroups();
        if (mcpGroups != null && !mcpGroups.isEmpty()) {
            List<McpToolInfo> mcpTools = mcpGroupManager.getToolsByGroups(mcpGroups);
            for (McpToolInfo mcp : mcpTools) {
                ToolSpecification spec = ToolSpecification.builder()
                    .name(mcp.getName())
                    .description(mcp.getDescription())
                    .parameters(mcp.getParameters())
                    .build();
                result.add(spec);
                log.debug("注册 MCP 工具: {}", mcp.getName());
            }
        }

        log.info("Agent[{}] 解析工具列表完成，共 {} 个工具", agentDef.getId(), result.size());
        return result;
    }

    /**
     * 执行工具调用，返回 ToolExecutionResultMessage（可直接追加到 messages 列表）
     *
     * @param request 来自 AiMessage.toolExecutionRequests() 的单个请求
     * @param context 当前 Agent 上下文
     */
    public ToolExecutionResultMessage execute(ToolExecutionRequest request, AgentContext context) {
        String toolName = request.name();
        log.info("执行工具: name={}, arguments={}", toolName, request.arguments());

        try {
            // 优先匹配系统内置工具
            for (SystemTool tool : systemTools) {
                if (tool.getName().equals(toolName)) {
                    String result = tool.execute(request.arguments(), context);
                    return ToolExecutionResultMessage.from(request, result);
                }
            }

            // 兜底走 MCP 工具
            McpToolInfo toolInfo = mcpGroupManager.getToolByName(toolName);
            if (toolInfo != null) {
                ToolExecutionResult mcpResult = mcpToolExecutor.execute(toolInfo, request.arguments());
                String resultText = mcpResult != null ? mcpResult.resultText() : "执行成功（无返回值）";
                return ToolExecutionResultMessage.from(request, resultText);
            }

            log.warn("工具未找到: {}", toolName);
            return ToolExecutionResultMessage.from(request, "工具未找到: " + toolName);

        } catch (Exception e) {
            log.error("工具执行失败: name={}", toolName, e);
            return ToolExecutionResultMessage.builder()
                .id(request.id())
                .toolName(toolName)
                .text("工具执行失败: " + e.getMessage())
                .isError(true)
                .build();
        }
    }

    /**
     * 判断是否需要启用渐进式工具加载模式
     * 需同时满足两个条件：
     * 1. Agent 的 systemTools 中声明了 RESOLVE_TOOLS（即 system_resolve_tools）
     * 2. MCP 工具数量超过配置的阈值
     */
    public boolean isProgressiveMode(AgentDefinition agentDef) {
        if (agentDef == null || agentDef.getTools() == null) {
            return false;
        }

        // 条件1：Agent 必须显式声明 RESOLVE_TOOLS 系统工具
        List<String> declaredSystemTools = agentDef.getTools().getSystemTools();
        if (declaredSystemTools == null) {
            return false;
        }
        boolean hasResolveTools = declaredSystemTools.stream().anyMatch(name ->
            "RESOLVE_TOOLS".equalsIgnoreCase(name) || "system_resolve_tools".equalsIgnoreCase(name)
        );
        if (!hasResolveTools) {
            return false;
        }

        // 条件2：MCP 工具数量超过阈值
        List<String> mcpGroups = agentDef.getTools().getMcpGroups();
        if (mcpGroups == null || mcpGroups.isEmpty()) {
            return false;
        }
        List<McpToolInfo> mcpTools = mcpGroupManager.getToolsByGroups(mcpGroups);
        int threshold = agentConfig.getTools().getProgressiveThreshold();
        return mcpTools.size() > threshold;
    }

    /**
     * 构建 MCP 工具概览文本（仅包含名称和描述，无参数）
     * 用于渐进式加载模式下追加到 System Prompt
     */
    public String buildMcpToolSummary(AgentDefinition agentDef) {
        if (agentDef == null || agentDef.getTools() == null) {
            return "";
        }
        List<String> mcpGroups = agentDef.getTools().getMcpGroups();
        if (mcpGroups == null || mcpGroups.isEmpty()) {
            return "";
        }

        List<McpToolInfo> mcpTools = mcpGroupManager.getToolsByGroups(mcpGroups);
        if (mcpTools.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("\n\n## 可选工具列表（详情按需加载）\n");
        summary.append("以下工具当前未加载完整定义，如需使用，请先调用 system_resolve_tools 工具传入工具名列表：\n\n");

        for (McpToolInfo tool : mcpTools) {
            summary.append("- **").append(tool.getName()).append("**: ");
            summary.append(tool.getDescription() != null ? tool.getDescription() : "无描述");
            summary.append("\n");
        }

        log.info("构建 MCP 工具概览，共 {} 个工具", mcpTools.size());
        return summary.toString();
    }

    /**
     * 解析指定工具名的 ToolSpecification（用于渐进式加载动态补充）
     * 
     * @param toolNames 需要加载的工具名称集合
     * @return 对应的 ToolSpecification 列表
     */
    public List<ToolSpecification> resolveActiveToolSpecifications(Collection<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return new ArrayList<>();
        }

        List<ToolSpecification> result = new ArrayList<>();
        for (String toolName : toolNames) {
            McpToolInfo toolInfo = mcpGroupManager.getToolByName(toolName);
            if (toolInfo != null) {
                ToolSpecification spec = ToolSpecification.builder()
                    .name(toolInfo.getName())
                    .description(toolInfo.getDescription())
                    .parameters(toolInfo.getParameters())
                    .build();
                result.add(spec);
                log.debug("动态加载 MCP 工具: {}", toolName);
            } else {
                log.warn("动态加载工具失败，工具未找到: {}", toolName);
            }
        }

        log.info("动态加载 {} 个 MCP 工具定义", result.size());
        return result;
    }
}
