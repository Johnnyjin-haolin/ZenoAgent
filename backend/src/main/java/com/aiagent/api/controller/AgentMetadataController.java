package com.aiagent.api.controller;

import com.aiagent.api.dto.McpGroupInfo;
import com.aiagent.api.dto.McpToolInfo;
import com.aiagent.infrastructure.config.AgentConfig;
import com.aiagent.common.response.ErrorCode;
import com.aiagent.common.enums.ModelType;
import com.aiagent.infrastructure.external.mcp.McpGroupManager;
import com.aiagent.api.dto.ModelInfoVO;
import com.aiagent.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent 配置与元数据接口
 */
@Slf4j
@RestController
@RequestMapping("/aiagent")
public class AgentMetadataController {

    @Autowired
    private AgentConfig agentConfig;

    @Autowired
    private McpGroupManager mcpGroupManager;

    /**
     * 获取可用模型列表
     */
    @GetMapping("/models/available")
    public Result<List<ModelInfoVO>> getAvailableModels(@RequestParam(required = false) String type) {
        List<AgentConfig.LLMConfig.ModelDefinition> modelDefinitions =
            agentConfig.getLlm().getModels();

        if (modelDefinitions == null || modelDefinitions.isEmpty()) {
            log.warn("未配置任何模型，返回空列表");
            return Result.success(Collections.emptyList());
        }

        String defaultModelId = agentConfig.getModel().getDefaultModelId();

        ModelType filterType = null;
        if (type != null && !type.trim().isEmpty()) {
            filterType = ModelType.fromCode(type);
        }

        List<ModelInfoVO> models = new ArrayList<>();
        int sort = 1;

        for (AgentConfig.LLMConfig.ModelDefinition modelDef : modelDefinitions) {
            ModelType modelType = ModelType.fromCode(modelDef.getType());

            if (filterType != null && modelType != filterType) {
                continue;
            }

            boolean isDefault = defaultModelId != null && defaultModelId.equals(modelDef.getId());

            String displayName = modelDef.getName() != null && !modelDef.getName().isEmpty()
                ? modelDef.getName()
                : modelDef.getId();

            String description = generateDescription(modelDef);

            ModelInfoVO modelInfo = ModelInfoVO.builder()
                .id(modelDef.getId())
                .name(modelDef.getName())
                .displayName(displayName)
                .description(description)
                .provider(modelDef.getProvider())
                .type(modelType.getCode())
                .sort(sort++)
                .isDefault(isDefault)
                .build();
            models.add(modelInfo);
        }

        models.sort((a, b) -> {
            if (Boolean.TRUE.equals(a.getIsDefault())) return -1;
            if (Boolean.TRUE.equals(b.getIsDefault())) return 1;
            int sortCompare = Integer.compare(
                a.getSort() != null ? a.getSort() : Integer.MAX_VALUE,
                b.getSort() != null ? b.getSort() : Integer.MAX_VALUE
            );
            return sortCompare != 0 ? sortCompare : a.getId().compareTo(b.getId());
        });

        log.debug("获取模型列表成功，类型筛选: {}, 返回数量: {}", type, models.size());
        return Result.success(models);
    }
    

    /**
     * 获取MCP分组列表
     */
    @GetMapping("/mcp/groups")
    public Result<List<McpGroupInfo>> getMcpGroups() {
        List<McpGroupInfo> groups = mcpGroupManager.getEnabledGroups();
        return Result.success(groups);
    }

    /**
     * 获取MCP分组详情
     */
    @GetMapping("/mcp/groups/{groupId}")
    public Result<McpGroupInfo> getMcpGroup(@PathVariable String groupId) {
        McpGroupInfo group = mcpGroupManager.getGroupById(groupId);
        if (group != null) {
            return Result.success(group);
        }
        return Result.error(ErrorCode.NOT_FOUND, "分组不存在");
    }

    /**
     * 获取MCP工具列表
     * 支持按分组筛选
     */
    @GetMapping("/mcp/tools")
    public Result<List<McpToolInfo>> getMcpTools(@RequestParam(required = false) List<String> groups) {
        List<McpToolInfo> tools = mcpGroupManager.getToolsByGroups(groups);
        return Result.success(tools);
    }

    /**
     * 生成模型描述
     */
    private String generateDescription(AgentConfig.LLMConfig.ModelDefinition modelDef) {
        String provider = modelDef.getProvider();
        if (provider == null) {
            return "AI模型";
        }
        switch (provider.toUpperCase()) {
            case "OPENAI":
                return "OpenAI官方模型";
            case "ZHIPU":
                return "智谱AI模型";
            case "DEEPSEEK":
                return "DeepSeek模型";
            case "QWEN":
                return "通义千问模型";
            case "GLM":
                return "GLM模型";
            default:
                return provider + "模型";
        }
    }
}

