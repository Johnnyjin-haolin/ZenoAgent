package com.aiagent.domain.tool;

import com.aiagent.api.dto.McpToolInfo;
import com.aiagent.api.dto.PersonalMcpToolSchema;
import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.agent.AgentDefinition;
import com.aiagent.infrastructure.config.AgentConfig;
import com.aiagent.infrastructure.external.mcp.McpManager;
import com.aiagent.infrastructure.external.mcp.McpToolExecutor;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一工具注册表
 * <p>
 * 合并系统内置工具（SystemTool）和 MCP 工具（GLOBAL / PERSONAL），
 * 对外提供统一的 resolveToolSpecifications 和 execute 接口。
 * <p>
 * PERSONAL 工具识别：通过 toolName 前缀 "personal:" 标记，
 * 对应 McpToolInfo.isPersonal()==true 或 serverId 在 personalMcpCapabilities 中
 */
@Slf4j
@Component
public class ToolRegistry {

    @Autowired
    private List<SystemTool> systemTools;

    @Autowired
    private McpManager mcpManager;

    @Autowired
    private McpToolExecutor mcpToolExecutor;

    @Autowired
    private AgentConfig agentConfig;

    /**
     * 根据 AgentDefinition 组装 GLOBAL ToolSpecification 列表
     * （PERSONAL 工具由 AgentContextService 运行时注入，此处不包含）
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
                    .filter(t -> t.getName().equals(sysToolName)
                            || t.getName().equalsIgnoreCase("system_" + sysToolName.toLowerCase()))
                    .findFirst()
                    .ifPresent(t -> {
                        result.add(t.getSpecification());
                        log.debug("注册系统工具: {}", t.getName());
                    });
            }
        }

        // 2. GLOBAL MCP 工具（按 serverMcpIds 过滤）
        List<String> serverMcpIds = agentDef.getTools().getServerMcpIds();
        List<McpToolInfo> mcpTools = mcpManager.getToolsByServerIds(serverMcpIds);
        for (McpToolInfo mcp : mcpTools) {
            ToolSpecification spec = ToolSpecification.builder()
                .name(mcp.getName())
                .description(mcp.getDescription())
                .parameters(mcp.getParameters())
                .build();
            result.add(spec);
            log.debug("注册 GLOBAL MCP 工具: {}", mcp.getName());
        }

        log.info("Agent[{}] 解析 GLOBAL 工具列表完成，共 {} 个工具", agentDef.getId(), result.size());
        return result;
    }

    /**
     * 在已有 ToolSpec 列表中追加 PERSONAL MCP 工具（新方案：使用前端上传的真实 schema）
     * <p>
     * 工具名保持原始名称，通过 personalToolServerId 映射表路由执行。
     * 不再注入占位假工具，而是直接将前端 prefetch 到的真实工具 schema 构造为 ToolSpecification。
     *
     * @param existing       已有的 ToolSpec 列表
     * @param personalSchemas 前端上传的 PERSONAL MCP 工具 schema 列表
     * @return 追加后的 ToolSpec 列表（不修改原列表）
     */
    public List<ToolSpecification> appendPersonalToolSpecs(
            List<ToolSpecification> existing,
            List<PersonalMcpToolSchema> personalSchemas) {
        if (personalSchemas == null || personalSchemas.isEmpty()) {
            return existing;
        }
        List<ToolSpecification> combined = new ArrayList<>(existing);
        for (PersonalMcpToolSchema schema : personalSchemas) {
            try {
                JsonObjectSchema parameters = parseInputSchema(schema.getInputSchema());
                ToolSpecification spec = ToolSpecification.builder()
                    .name(schema.getToolName())
                    .description(schema.getDescription() != null ? schema.getDescription() : "")
                    .parameters(parameters)
                    .build();
                combined.add(spec);
                log.debug("注册 PERSONAL MCP 工具: {}, serverId={}", schema.getToolName(), schema.getServerId());
            } catch (Exception e) {
                log.warn("PERSONAL MCP 工具 schema 解析失败，跳过: toolName={}, error={}",
                    schema.getToolName(), e.getMessage());
            }
        }
        return combined;
    }

    /**
     * 构建 personalToolMap（真实工具名 → serverId），基于前端上传的 schema 列表
     * 供 FunctionCallingEngine 在推理开始时构建，用于识别 LLM 调用的是否是 PERSONAL 工具
     */
    public Map<String, String> buildPersonalToolMapFromSchemas(List<PersonalMcpToolSchema> personalSchemas) {
        Map<String, String> map = new HashMap<>();
        if (personalSchemas != null) {
            for (PersonalMcpToolSchema schema : personalSchemas) {
                if (schema.getToolName() != null && schema.getServerId() != null) {
                    map.put(schema.getToolName(), schema.getServerId());
                }
            }
        }
        return map;
    }

    /**
     * 判断工具名是否属于 PERSONAL MCP
     */
    public String getPersonalServerId(String toolName, Map<String, String> personalToolMap) {
        return personalToolMap != null ? personalToolMap.get(toolName) : null;
    }

    /**
     * 将前端传入的原始 inputSchema（Map/Object）解析为 JsonObjectSchema
     */
    private JsonObjectSchema parseInputSchema(Object inputSchema) {
        if (inputSchema == null) {
            return JsonObjectSchema.builder().build();
        }
        try {
            String json = JSON.toJSONString(inputSchema);
            return JSON.parseObject(json, JsonObjectSchema.class);
        } catch (Exception e) {
            log.warn("inputSchema 反序列化失败，使用空 schema: {}", e.getMessage());
            return JsonObjectSchema.builder().build();
        }
    }

    /**
     * 执行 GLOBAL 工具调用（系统工具 / GLOBAL MCP）
     * PERSONAL 工具不走此方法，由 FunctionCallingEngine 通过 SSE 下发
     */
    public ToolExecutionResultMessage execute(ToolExecutionRequest request, AgentContext context) {
        String toolName = request.name();
        log.info("执行 GLOBAL 工具: name={}, arguments={}", toolName, request.arguments());

        try {
            // 优先匹配系统内置工具
            for (SystemTool tool : systemTools) {
                if (tool.getName().equals(toolName)) {
                    String result = tool.execute(request.arguments(), context);
                    return ToolExecutionResultMessage.from(request, result);
                }
            }

            // 兜底走 GLOBAL MCP 工具
            McpToolInfo toolInfo = mcpManager.getToolByName(toolName);
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
     */
    public boolean isProgressiveMode(AgentDefinition agentDef) {
        if (agentDef == null || agentDef.getTools() == null) {
            return false;
        }

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

        List<String> serverMcpIds = agentDef.getTools().getServerMcpIds();
        List<McpToolInfo> mcpTools = mcpManager.getToolsByServerIds(serverMcpIds);
        int threshold = agentConfig.getTools().getProgressiveThreshold();
        return mcpTools.size() > threshold;
    }

    /**
     * 构建 MCP 工具概览文本（渐进式加载模式使用）
     */
    public String buildMcpToolSummary(AgentDefinition agentDef) {
        if (agentDef == null || agentDef.getTools() == null) {
            return "";
        }
        List<String> serverMcpIds = agentDef.getTools().getServerMcpIds();
        List<McpToolInfo> mcpTools = mcpManager.getToolsByServerIds(serverMcpIds);
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
     * 解析指定工具名的 ToolSpecification（渐进式加载动态补充）
     */
    public List<ToolSpecification> resolveActiveToolSpecifications(Collection<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return new ArrayList<>();
        }

        List<ToolSpecification> result = new ArrayList<>();
        for (String toolName : toolNames) {
            McpToolInfo toolInfo = mcpManager.getToolByName(toolName);
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
