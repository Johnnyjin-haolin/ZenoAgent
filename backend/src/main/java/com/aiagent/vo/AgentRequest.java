package com.aiagent.vo;

import com.aiagent.model.AgentMode;
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
     * Agent配置ID
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
}


