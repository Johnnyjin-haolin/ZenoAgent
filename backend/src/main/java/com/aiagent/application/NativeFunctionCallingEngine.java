package com.aiagent.application;

import com.aiagent.api.dto.AgentEventData;
import com.aiagent.common.constant.AgentConstants;
import com.aiagent.domain.agent.AgentDefinition;
import com.aiagent.domain.agent.AgentDefinitionLoader;
import com.aiagent.domain.llm.SimpleLLMChatHandler;
import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.tool.ToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class NativeFunctionCallingEngine {

    private static final int MAX_TOOL_ROUNDS = 8;

    @Autowired
    private SimpleLLMChatHandler llmChatHandler;

    @Autowired
    private AgentDefinitionLoader agentDefinitionLoader;

    @Autowired
    private ToolRegistry toolRegistry;

    public void execute(AgentContext context) {
        String agentId = context.getAgentId();
        AgentDefinition agentDef = agentDefinitionLoader.getById(agentId);
        if (agentDef == null) {
            log.warn("AgentDefinition 未找到: agentId={}，使用默认 Agent", agentId);
            agentDef = agentDefinitionLoader.getById(AgentConstants.DEFAULT_AGENT_ID);
        }
        List<ChatMessage> messages = buildMessages(agentDef, context);
        List<ToolSpecification> toolSpecs = agentDef != null
            ? toolRegistry.resolveToolSpecifications(agentDef)
            : new ArrayList<>();
        int toolRound = 0;
        while (toolRound <= MAX_TOOL_ROUNDS) {
            log.info("Function Calling 循环第 {} 轮，toolRound={}", toolRound, toolRound);
            publishEvent(context, AgentConstants.EVENT_AGENT_THINKING, "AI 正在思考...");
            ChatResponse response = llmChatHandler.chatWithToolsStreaming(
                context.getModelId(), messages, toolSpecs, context.getStreamingCallback()
            );
            if (response == null) {
                log.error("LLM 返回空响应");
                break;
            }
            AiMessage aiMessage = response.aiMessage();
            messages.add(aiMessage);
            context.addMessage(aiMessage);
            FinishReason finishReason = response.finishReason();
            log.info("LLM 返回 finishReason={}", finishReason);
            if (FinishReason.TOOL_EXECUTION.equals(finishReason) && aiMessage.hasToolExecutionRequests()) {
                toolRound++;
                List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                publishEvent(context, AgentConstants.EVENT_AGENT_TOOL_EXECUTING,
                    "正在执行工具: " + toolRequests.get(0).name());
                for (ToolExecutionRequest toolRequest : toolRequests) {
                    publishEvent(context, AgentConstants.EVENT_AGENT_TOOL_CALL,
                        "调用工具: " + toolRequest.name());
                    ToolExecutionResultMessage resultMsg =
                        toolRegistry.execute(toolRequest, context);
                    messages.add(resultMsg);
                    context.addMessage(resultMsg);
                    publishEvent(context, AgentConstants.EVENT_AGENT_TOOL_RESULT,
                        "工具执行完成: " + toolRequest.name());
                }
                continue;
            }
            log.info("Function Calling 循环结束，共执行 {} 轮工具调用", toolRound);
            break;
        }
        if (toolRound > MAX_TOOL_ROUNDS) {
            log.warn("工具调用轮次超限，强制终止推理循环");
        }
    }
    private List<ChatMessage> buildMessages(AgentDefinition agentDef, AgentContext context) {
        List<ChatMessage> messages = new ArrayList<>();
        if (agentDef != null && agentDef.getSystemPrompt() != null && !agentDef.getSystemPrompt().isEmpty()) {
            messages.add(SystemMessage.from(agentDef.getSystemPrompt()));
        }
        List<ChatMessage> history = context.getMessages();
        if (history != null) {
            messages.addAll(history);
        }
        return messages;
    }

    private void publishEvent(AgentContext context, String event, String message) {
        if (context.getEventPublisher() != null) {
            try {
                context.getEventPublisher().accept(
                    AgentEventData.builder()
                        .event(event)
                        .message(message)
                        .conversationId(context.getConversationId())
                        .build()
                );
            } catch (Exception e) {
                log.debug("发布事件失败: event={}", event, e);
            }
        }
    }
}
