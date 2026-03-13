package com.aiagent.application;

import com.aiagent.api.dto.AgentEventData;
import com.aiagent.common.constant.AgentConstants;
import com.aiagent.common.enums.AgentState;
import com.aiagent.domain.agent.AgentDefinition;
import com.aiagent.domain.agent.AgentDefinitionLoader;
import com.aiagent.domain.llm.SimpleLLMChatHandler;
import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.model.bo.AgentExecutionResult;
import com.aiagent.domain.tool.ToolRegistry;
import com.aiagent.domain.tool.todo.TodoItem;
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

/**
 * 基于 LangChain4j 原生 Function Calling 的标准 Agent 推理引擎（默认引擎）
 *
 * <p>适用于支持 Function Calling 的主流模型（GPT-4、Claude、DeepSeek R1、Qwen3 等）。
 * 通过 {@code toolSpecifications} 将工具定义以结构化参数传递给 LLM，
 * 依据 {@code FinishReason.TOOL_EXECUTION} 驱动推理循环，无需 Prompt 解析。
 *
 * <p>如需使用不支持 Function Calling 的模型，切换 {@code AgentServiceImpl} 中的
 * {@code @Qualifier} 值为 {@code "promptReActEngine"}。
 *
 * @see PromptReActEngine
 */
@Slf4j
@Component
public class FunctionCallingEngine implements AgentEngine {

    private static final int MAX_TOOL_ROUNDS = 8;

    @Autowired
    private SimpleLLMChatHandler llmChatHandler;

    @Autowired
    private AgentDefinitionLoader agentDefinitionLoader;

    @Autowired
    private ToolRegistry toolRegistry;

    @Override
    public AgentExecutionResult execute(AgentContext context) {
        long startNs = System.nanoTime();
        String agentId = context.getAgentId();
        AgentDefinition agentDef = agentDefinitionLoader.getById(agentId);
        if (agentDef == null) {
            log.warn("AgentDefinition 未找到: agentId={}，使用默认 Agent", agentId);
            agentDef = agentDefinitionLoader.getById(AgentConstants.DEFAULT_AGENT_ID);
        }

        // 判断是否启用渐进式加载
        boolean progressiveMode = toolRegistry.isProgressiveMode(agentDef);
        log.info("渐进式工具加载模式: {}", progressiveMode ? "已启用" : "未启用");

        // 初始化 messages（渐进式模式下追加工具概览到 System Prompt）
        List<ChatMessage> messages = buildMessages(agentDef, context, progressiveMode);

        // 初始化 toolSpecs（渐进式模式下仅包含系统工具，不加载 MCP 工具）
        List<ToolSpecification> toolSpecs = resolveInitialToolSpecs(agentDef, progressiveMode);

        int toolRound = 0;
        
        try {
            while (toolRound <= MAX_TOOL_ROUNDS) {
                log.info("Function Calling 循环第 {} 轮，toolRound={}", toolRound, toolRound);
                publishEvent(context, AgentConstants.EVENT_AGENT_THINKING, "AI 正在思考...");
                ChatResponse response = llmChatHandler.chatWithToolsStreaming(
                    context.getModelId(), messages, toolSpecs, context.getStreamingCallback()
                );
                if (response == null) {
                    log.error("LLM 返回空响应");
                    long durationMs = elapsedMs(startNs);
                    return AgentExecutionResult.failure("LLM 返回空响应", "NULL_RESPONSE", 
                        context.getMessages(), toolRound, durationMs, AgentState.FAILED);
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

                    // 渐进式加载：检测是否有新的工具加载请求
                    if (progressiveMode && context.getActiveMcpToolNames() != null 
                        && !context.getActiveMcpToolNames().isEmpty()) {
                        List<ToolSpecification> newToolSpecs = 
                            toolRegistry.resolveActiveToolSpecifications(context.getActiveMcpToolNames());
                        toolSpecs.addAll(newToolSpecs);
                        log.info("动态加载 {} 个 MCP 工具，当前 toolSpecs 共 {} 个", 
                            newToolSpecs.size(), toolSpecs.size());
                        // 清空已消费的工具名列表
                        context.getActiveMcpToolNames().clear();
                    }

                    continue;
                }
                log.info("Function Calling 循环结束，共执行 {} 轮工具调用", toolRound);
                break;
            }
            if (toolRound > MAX_TOOL_ROUNDS) {
                log.warn("工具调用轮次超限，强制终止推理循环");
                long durationMs = elapsedMs(startNs);
                return AgentExecutionResult.failure("工具调用轮次超限", "MAX_ITERATIONS_EXCEEDED",
                    context.getMessages(), toolRound, durationMs, AgentState.FAILED);
            }
            
            long durationMs = elapsedMs(startNs);
            return AgentExecutionResult.success(context.getMessages(), toolRound, durationMs, AgentState.COMPLETED);
            
        } catch (Exception e) {
            log.error("NativeFunctionCalling 执行异常", e);
            publishEvent(context, AgentConstants.EVENT_AGENT_ERROR, "执行失败: " + e.getMessage());
            long durationMs = elapsedMs(startNs);
            return AgentExecutionResult.failure(e.getMessage(), "EXCEPTION",
                context.getMessages(), toolRound, durationMs, AgentState.FAILED);
        }
    }
    /**
     * 构建消息列表（渐进式模式下追加工具概览到 System Prompt，有未完成 Todo 时追加 Todo 清单）
     */
    private List<ChatMessage> buildMessages(AgentDefinition agentDef, AgentContext context, boolean progressiveMode) {
        List<ChatMessage> messages = new ArrayList<>();
        
        if (agentDef != null && agentDef.getSystemPrompt() != null && !agentDef.getSystemPrompt().isEmpty()) {
            String systemPrompt = agentDef.getSystemPrompt();
            
            // 渐进式模式：追加 MCP 工具概览
            if (progressiveMode) {
                String toolSummary = toolRegistry.buildMcpToolSummary(agentDef);
                if (!toolSummary.isEmpty()) {
                    systemPrompt += toolSummary;
                }
            }

            // Todo 清单注入：有未完成任务时追加到系统提示词
            String todoSection = buildTodoSection(context);
            if (!todoSection.isEmpty()) {
                systemPrompt += todoSection;
            }
            
            messages.add(SystemMessage.from(systemPrompt));
        }
        
        List<ChatMessage> history = context.getMessages();
        if (history != null) {
            messages.addAll(history);
        }
        return messages;
    }

    /**
     * 构建 Todo 清单提示词片段
     * 仅当存在未完成（pending）的 Todo 时才返回非空字符串
     */
    private String buildTodoSection(AgentContext context) {
        List<TodoItem> todos = context.getTodos();
        if (todos == null || todos.isEmpty()) {
            return "";
        }

        boolean hasPending = todos.stream()
            .anyMatch(t -> TodoItem.TodoStatus.pending == t.getStatus());
        if (!hasPending) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 当前任务清单\n\n");
        sb.append("> 你有未完成的任务，请优先推进以下待办项，完成每项后及时调用 system_todo_write 更新状态。\n\n");

        todos.stream()
            .sorted((a, b) -> {
                if (a.getStatus() != b.getStatus()) {
                    if (a.getStatus() == TodoItem.TodoStatus.pending) return -1;
                    if (b.getStatus() == TodoItem.TodoStatus.pending) return 1;
                }
                return Integer.compare(a.getPriority(), b.getPriority());
            })
            .forEach(item -> {
                String mark = switch (item.getStatus()) {
                    case completed -> "- [x]";
                    case cancelled -> "- [-]";
                    default        -> "- [ ]";
                };
                sb.append(mark)
                  .append(" [P").append(item.getPriority()).append("]")
                  .append(" (id: ").append(item.getId()).append(") ")
                  .append(item.getContent()).append("\n");
            });

        return sb.toString();
    }

    /**
     * 解析初始 ToolSpecification 列表
     * 渐进式模式下仅包含系统工具，非渐进式模式包含全量工具
     */
    private List<ToolSpecification> resolveInitialToolSpecs(AgentDefinition agentDef, boolean progressiveMode) {
        List<ToolSpecification> result = new ArrayList<>();
        
        if (agentDef == null || agentDef.getTools() == null) {
            return result;
        }
        
        // 获取全量工具定义（供过滤使用）
        List<ToolSpecification> allSpecs = toolRegistry.resolveToolSpecifications(agentDef);

        // 系统工具：始终加载
        allSpecs.stream()
            .filter(spec -> spec.name().startsWith("system_"))
            .forEach(result::add);

        // MCP 工具：渐进式模式下不加载（通过 system_resolve_tools 按需加载）
        if (!progressiveMode) {
            allSpecs.stream()
                .filter(spec -> !spec.name().startsWith("system_"))
                .forEach(result::add);
        }
        
        log.info("初始化工具列表，渐进式模式={}, toolSpecs数量={}", progressiveMode, result.size());
        return result;
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

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000;
    }
}
