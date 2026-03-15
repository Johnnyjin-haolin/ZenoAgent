package com.aiagent.api.dto;

import com.aiagent.common.enums.AgentMode;
import com.aiagent.domain.agent.AgentDefinition;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 请求参数
 */
@Data
public class AgentRequest {

    /** 用户输入内容 */
    private String content;

    /** 会话 ID */
    private String conversationId;

    /** Agent 配置 ID，默认值为 DEFAULT_AGENT_ID */
    private String agentId;

    /** 关联知识库 ID 列表 */
    private List<String> knowledgeIds;

    /**
     * 本次会话 MCP 服务器工具细粒度选择（为空则使用 Agent 默认配置）
     */
    private List<AgentDefinition.McpServerSelection> mcpServers;

    /**
     * 本次会话启用的系统内置工具名称列表（为空则使用 Agent 默认配置）
     */
    private List<String> systemTools;

    /**
     * PERSONAL MCP 工具 Schema 列表（由前端 prefetch 后随请求上传）
     * <p>
     * 前端在发送消息前先调用各 PERSONAL MCP 服务器的 tools/list 接口，
     * 获取真实工具 schema 后将其放入此字段一起上传。
     * 后端直接用这些 schema 构造 ToolSpecification，不再注入占位假工具。
     * <p>
     * 若前端 prefetch 失败（网络不通 / 密钥缺失），此字段为空或不含对应工具，
     * 该服务器的工具对本次对话不可用（不阻塞对话）。
     */
    private List<PersonalMcpToolSchema> personalMcpTools;

    /** 上下文参数 */
    private Map<String, Object> context;

    /** 执行模式（AUTO / MANUAL） */
    private AgentMode mode = AgentMode.AUTO;

    /** 指定的模型 ID（可选） */
    private String modelId;
}
