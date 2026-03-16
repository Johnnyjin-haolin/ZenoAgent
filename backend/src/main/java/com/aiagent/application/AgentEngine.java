package com.aiagent.application;

import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.model.bo.AgentExecutionResult;

/**
 * Agent 推理引擎统一接口
 * 所有推理引擎（ReAct、NativeFunctionCalling 等）均实现此接口，
 * 使 AgentServiceImpl 面向接口编程，便于引擎切换。
 */
public interface AgentEngine {

    /**
     * 执行 Agent 推理循环
     *
     * @param context Agent 上下文（含消息历史、modelId、eventPublisher、streamingCallback 等）
     * @return 执行结果
     */
    AgentExecutionResult execute(AgentContext context);
}
