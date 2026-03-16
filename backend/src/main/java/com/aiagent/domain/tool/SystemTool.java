package com.aiagent.domain.tool;

import com.aiagent.domain.model.bo.AgentContext;
import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * 系统内置工具接口
 * 实现类需注册为 Spring Bean，由 ToolRegistry 自动收集
 *
 * @author aiagent
 */
public interface SystemTool {

    /**
     * 工具名称（唯一标识，建议使用 system_ 前缀）
     */
    String getName();

    /**
     * 返回标准 langchain4j ToolSpecification，供 ChatRequest.toolSpecifications() 使用
     */
    ToolSpecification getSpecification();

    /**
     * 执行工具调用
     *
     * @param jsonArguments LLM 生成的参数 JSON 字符串（对应 ToolExecutionRequest.arguments()）
     * @param context       当前 Agent 上下文（用于发 SSE 事件、访问知识库等）
     * @return 工具执行结果文本（将被包装为 ToolExecutionResultMessage.text()）
     */
    String execute(String jsonArguments, AgentContext context);
}
