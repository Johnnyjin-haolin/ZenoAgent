package com.aiagent.api.controller;

import com.aiagent.api.dto.AgentDefinitionRequest;
import com.aiagent.api.dto.AgentDefinitionVO;
import com.aiagent.api.dto.McpGroupInfo;
import com.aiagent.api.dto.Page;
import com.aiagent.api.dto.PageResult;
import com.aiagent.common.response.Result;
import com.aiagent.domain.agent.AgentDefinition;
import com.aiagent.domain.agent.AgentDefinitionLoader;
import com.aiagent.domain.model.entity.AgentDefinitionEntity;
import com.aiagent.domain.tool.SystemTool;
import com.aiagent.infrastructure.external.mcp.McpGroupManager;
import com.aiagent.infrastructure.mapper.AgentDefinitionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agent 定义管理接口
 * <p>
 * 提供 Agent 的 CRUD 和工具配置辅助接口
 */
@Slf4j
@RestController
@RequestMapping("/aiagent/agent-definitions")
@RequiredArgsConstructor
public class AgentDefinitionController {

    private final AgentDefinitionMapper agentDefinitionMapper;
    private final AgentDefinitionLoader agentDefinitionLoader;
    private final McpGroupManager mcpGroupManager;
    private final List<SystemTool> systemTools;
    private final ObjectMapper objectMapper;

    // ----------------------------------------------------------------- 查询

    /**
     * 获取 Agent 定义列表（内置优先，支持分页）
     * <p>
     * pageNo 和 pageSize 均为可选，不传时返回全量数据（用于兼容老调用方）。
     * 前端分页滚动场景请传分页参数。
     */
    @GetMapping
    public Result<?> listAgents(
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false, defaultValue = "12") Integer pageSize) {

        // 未传 pageNo 时走全量接口（向后兼容）
        if (pageNo == null) {
            List<AgentDefinitionEntity> entities = agentDefinitionMapper.selectAll();
            List<AgentDefinitionVO> vos = entities.stream()
                    .map(this::toVO)
                    .collect(Collectors.toList());
            return Result.success(vos);
        }

        // 分页查询
        int validPage = Math.max(pageNo, 1);
        int validSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (validPage - 1) * validSize;

        List<AgentDefinitionEntity> entities = agentDefinitionMapper.selectPage(offset, validSize);
        long total = agentDefinitionMapper.count();

        List<AgentDefinitionVO> vos = entities.stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        Page<AgentDefinitionVO> page = new Page<>();
        page.setRecords(vos);
        page.setTotal(total);
        page.setCurrent(validPage);
        page.setSize(validSize);

        return Result.success(PageResult.from(page));
    }

    /**
     * 获取单个 Agent 定义
     */
    @GetMapping("/{id}")
    public Result<AgentDefinitionVO> getAgent(@PathVariable String id) {
        AgentDefinitionEntity entity = agentDefinitionMapper.selectById(id);
        if (entity == null) {
            return Result.error("Agent 不存在");
        }
        return Result.success(toVO(entity));
    }

    // ----------------------------------------------------------------- 创建

    /**
     * 创建用户自定义 Agent
     */
    @PostMapping
    public Result<AgentDefinitionVO> createAgent(@RequestBody AgentDefinitionRequest request) {
        if (!StringUtils.hasText(request.getName())) {
            return Result.error("Agent 名称不能为空");
        }

        AgentDefinitionEntity entity = new AgentDefinitionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setSystemPrompt(request.getSystemPrompt());
        entity.setIsBuiltin(0);
        entity.setStatus("active");
        entity.setToolsConfig(serializeTools(request.getTools()));
        entity.setContextConfig(serializeContextConfig(request.getContextConfig()));
        entity.setRagConfig(serializeRagConfig(request.getRagConfig()));

        agentDefinitionMapper.insert(entity);
        log.info("创建 Agent 定义: id={}, name={}", entity.getId(), entity.getName());

        AgentDefinitionEntity saved = agentDefinitionMapper.selectById(entity.getId());
        return Result.success("创建成功", toVO(saved));
    }

    // ----------------------------------------------------------------- 更新

    /**
     * 更新 Agent 定义（内置 Agent 仅允许修改 systemPrompt / tools / contextConfig / ragConfig）
     */
    @PutMapping("/{id}")
    public Result<AgentDefinitionVO> updateAgent(@PathVariable String id,
                                                  @RequestBody AgentDefinitionRequest request) {
        AgentDefinitionEntity existing = agentDefinitionMapper.selectById(id);
        if (existing == null) {
            return Result.error("Agent 不存在");
        }

        AgentDefinitionEntity update = new AgentDefinitionEntity();
        update.setId(id);
        if (existing.getIsBuiltin() == 0) {
            // 用户自建 Agent 允许修改所有字段
            update.setName(request.getName());
            update.setDescription(request.getDescription());
        }
        update.setSystemPrompt(request.getSystemPrompt());
        update.setToolsConfig(serializeTools(request.getTools()));
        update.setContextConfig(serializeContextConfig(request.getContextConfig()));
        update.setRagConfig(serializeRagConfig(request.getRagConfig()));

        agentDefinitionMapper.update(update);
        log.info("更新 Agent 定义: id={}", id);

        AgentDefinitionEntity updated = agentDefinitionMapper.selectById(id);
        return Result.success("更新成功", toVO(updated));
    }

    // ----------------------------------------------------------------- 删除

    /**
     * 删除 Agent 定义（内置 Agent 不允许删除）
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteAgent(@PathVariable String id) {
        AgentDefinitionEntity existing = agentDefinitionMapper.selectById(id);
        if (existing == null) {
            return Result.error("Agent 不存在");
        }
        if (existing.getIsBuiltin() == 1) {
            return Result.error("内置 Agent 不允许删除");
        }
        agentDefinitionMapper.deleteById(id);
        log.info("删除 Agent 定义: id={}", id);
        return Result.success("删除成功", true);
    }

    // ----------------------------------------------------------------- 辅助

    /**
     * 获取可用的 MCP 工具分组列表（用于配置页工具选择器）
     */
    @GetMapping("/available-mcp-groups")
    public Result<List<McpGroupInfo>> getAvailableMcpGroups() {
        List<McpGroupInfo> groups = mcpGroupManager.getEnabledGroups();
        return Result.success(groups);
    }

    /**
     * 获取可用的系统内置工具列表（用于配置页工具选择器）
     */
    @GetMapping("/available-system-tools")
    public Result<List<Map<String, String>>> getAvailableSystemTools() {
        List<Map<String, String>> result = new ArrayList<>();
        for (SystemTool tool : systemTools) {
            Map<String, String> item = new HashMap<>();
            item.put("name", tool.getName());
            String desc = tool.getSpecification() != null ? tool.getSpecification().description() : "";
            item.put("description", desc);
            result.add(item);
        }
        return Result.success(result);
    }

    // ----------------------------------------------------------------- 转换

    private AgentDefinitionVO toVO(AgentDefinitionEntity entity) {
        AgentDefinitionVO vo = new AgentDefinitionVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setSystemPrompt(entity.getSystemPrompt());
        vo.setBuiltin(entity.getIsBuiltin() != null && entity.getIsBuiltin() == 1);
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());

        if (entity.getToolsConfig() != null) {
            try {
                AgentDefinitionVO.ToolsConfigVO toolsVO =
                        objectMapper.readValue(entity.getToolsConfig(), AgentDefinitionVO.ToolsConfigVO.class);
                vo.setTools(toolsVO);
            } catch (Exception e) {
                log.warn("反序列化 tools 配置失败: id={}", entity.getId());
                vo.setTools(new AgentDefinitionVO.ToolsConfigVO());
            }
        } else {
            vo.setTools(new AgentDefinitionVO.ToolsConfigVO());
        }

        if (entity.getContextConfig() != null) {
            try {
                AgentDefinitionVO.ContextConfigVO ctxVO =
                        objectMapper.readValue(entity.getContextConfig(), AgentDefinitionVO.ContextConfigVO.class);
                vo.setContextConfig(ctxVO);
            } catch (Exception e) {
                log.warn("反序列化 contextConfig 失败: id={}", entity.getId());
            }
        }

        if (entity.getRagConfig() != null) {
            try {
                AgentDefinitionVO.RagConfigVO ragVO =
                        objectMapper.readValue(entity.getRagConfig(), AgentDefinitionVO.RagConfigVO.class);
                vo.setRagConfig(ragVO);
            } catch (Exception e) {
                log.warn("反序列化 ragConfig 失败: id={}", entity.getId());
            }
        }

        return vo;
    }

    private String serializeTools(AgentDefinitionRequest.ToolsConfigRequest tools) {
        if (tools == null) {
            return "{}";
        }
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("mcpGroups", tools.getMcpGroups() != null ? tools.getMcpGroups() : new ArrayList<>());
            map.put("systemTools", tools.getSystemTools() != null ? tools.getSystemTools() : new ArrayList<>());
            map.put("knowledgeIds", tools.getKnowledgeIds() != null ? tools.getKnowledgeIds() : new ArrayList<>());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("序列化 tools 配置失败", e);
            return "{}";
        }
    }

    private String serializeContextConfig(AgentDefinitionRequest.ContextConfigRequest cfg) {
        if (cfg == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(cfg);
        } catch (Exception e) {
            log.warn("序列化 contextConfig 失败", e);
            return null;
        }
    }

    private String serializeRagConfig(AgentDefinitionRequest.RagConfigRequest cfg) {
        if (cfg == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(cfg);
        } catch (Exception e) {
            log.warn("序列化 ragConfig 失败", e);
            return null;
        }
    }
}
