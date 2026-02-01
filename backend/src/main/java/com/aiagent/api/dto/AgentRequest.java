package com.aiagent.api.dto;

import com.aiagent.domain.enums.AgentMode;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 请求参数
 * 
 * @author aiagent
 */
@Data
public class AgentRequest {
    
    /**
     * 用户输入内容
     */
    private String content;
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * Agent配置ID，暂未泳道功能，默认值为DEFAULT_AGENT_ID
     */
    private String agentId;
    
    /**
     * 关联知识库ID列表
     */
    private List<String> knowledgeIds;
    
    /**
     * 启用的工具名称列表（为空则使用所有可用工具）
     */
    private List<String> enabledTools;
    
    /**
     * 启用的MCP分组列表（为空则使用所有可用分组）
     */
    private List<String> enabledMcpGroups;
    
    /**
     * 上下文参数
     */
    private Map<String, Object> context;
    
    /**
     * 执行模式
     */
    private AgentMode mode = AgentMode.AUTO;
    
    /**
     * 指定的模型ID（可选）
     */
    private String modelId;
    
    /**
     * 思考引擎配置（可选，不传则使用默认值）
     * 用于控制提示词构建时的历史长度、截断等行为
     */
    private ThinkingConfig thinkingConfig;
    
    /**
     * RAG配置（可选，不传则使用默认值）
     * 用于控制知识库检索的参数（检索数量、相似度阈值、内容长度限制等）
     */
    private RAGConfig ragConfig;
}


