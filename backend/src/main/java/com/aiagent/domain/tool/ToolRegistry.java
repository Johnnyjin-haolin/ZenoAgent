package com.aiagent.domain.tool;

import com.aiagent.api.dto.McpToolInfo;
import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.agent.AgentDefinition;
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
import java.util.List;

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
}
