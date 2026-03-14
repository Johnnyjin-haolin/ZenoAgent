package com.aiagent.application;

import com.aiagent.common.constant.AgentConstants;
import com.aiagent.common.enums.AgentState;
import com.aiagent.domain.agent.AgentDefinition;
import com.aiagent.domain.agent.AgentDefinitionLoader;
import com.aiagent.domain.llm.SimpleLLMChatHandler;
import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.model.bo.AgentExecutionResult;
import com.aiagent.domain.tool.ToolRegistry;
import com.aiagent.domain.tool.todo.TodoItem;
import com.alibaba.fastjson2.JSON;
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
import java.util.HashMap;
import java.util.List;

/**
 * 基于 LangChain4j 原生 Function Calling 的标准 Agent 推理引擎（默认引擎）
 *
 * <p>适用于支持 Function Calling 的主流模型（GPT-4、Claude、DeepSeek R1、Qwen3 等）。
 * 通过 {@code toolSpecifications} 将工具定义以结构化参数传递给 LLM，
 * 依据 {@code FinishReason.TOOL_EXECUTION} 驱动推理循环，无需 Prompt 解析。
 *
 * <p>引擎通过 {@link AgentEventPublisher} 接口发布进度事件，与传输协议完全解耦。
 * 如需使用不支持 Function Calling 的模型，切换 {@code AgentServiceImpl} 中的
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
        AgentEventPublisher publisher = context.getEventPublisher();

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
                log.info("Function Calling 循环第 {} 轮", toolRound);

                // 判断当前轮是否为工具轮（尚未确定），先用"思考"回调；
                // 若最终是工具轮则 token 路由到 onThinkingToken，否则路由到 onToken。
                // 通过 isToolRound 标记在轮次结束后决定——此处构建包装回调。
                final boolean[] isToolRound = {false};
                final StringBuilder roundBuffer = new StringBuilder();

                StreamingCallback roundCallback = buildRoundCallback(context, roundBuffer, isToolRound);

                ChatResponse response = llmChatHandler.chatWithToolsStreaming(
                    context.getModelId(), messages, toolSpecs, roundCallback
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
                    // 工具调用轮：把本轮缓冲的 token 作为思考内容发出
                    isToolRound[0] = true;
                    flushThinkingBuffer(publisher, roundBuffer);

                    toolRound++;
                    List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();

                    for (ToolExecutionRequest toolRequest : toolRequests) {
                        // 发布工具调用事件
                        if (publisher != null) {
                            publisher.onToolCall(toolRequest.name(), parseArguments(toolRequest.arguments()));
                        }

                        long toolStart = System.nanoTime();
                        ToolExecutionResultMessage resultMsg = toolRegistry.execute(toolRequest, context);
                        long toolDurationMs = (System.nanoTime() - toolStart) / 1_000_000;

                        messages.add(resultMsg);
                        context.addMessage(resultMsg);

                        // 发布工具结果事件
                        if (publisher != null) {
                            publisher.onToolResult(
                                toolRequest.name(),
                                resultMsg.text(),
                                toolDurationMs,
                                null
                            );
                        }
                    }

                    // 渐进式加载：检测是否有新的工具加载请求
                    if (progressiveMode && context.getActiveMcpToolNames() != null
                        && !context.getActiveMcpToolNames().isEmpty()) {
                        List<ToolSpecification> newToolSpecs =
                            toolRegistry.resolveActiveToolSpecifications(context.getActiveMcpToolNames());
                        toolSpecs.addAll(newToolSpecs);
                        log.info("动态加载 {} 个 MCP 工具，当前 toolSpecs 共 {} 个",
                            newToolSpecs.size(), toolSpecs.size());
                        context.getActiveMcpToolNames().clear();
                    }

                    continue;
                }

                // 最终回复轮：StreamingCallback 已通过 onToken 逐 token 发出，
                // 流式完成由原始 StreamingCallback.onComplete 负责发出 onStreamComplete。
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
            log.error("FunctionCallingEngine 执行异常", e);
            if (publisher != null) {
                publisher.onError("执行失败: " + e.getMessage());
            }
            long durationMs = elapsedMs(startNs);
            return AgentExecutionResult.failure(e.getMessage(), "EXCEPTION",
                context.getMessages(), toolRound, durationMs, AgentState.FAILED);
        }
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────────

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

    /**
     * 构建本轮的 StreamingCallback。
     *
     * <p>策略：所有轮次的流式 token 先缓冲到 {@code roundBuffer}。
     * <ul>
     *   <li>工具调用轮（{@code isToolRound[0]=true}）：由 {@link #flushThinkingBuffer} 统一发为 onThinkingToken</li>
     *   <li>最终回复轮（{@code isToolRound[0]=false}）：每个 token 实时路由给 {@code publisher.onToken}</li>
     * </ul>
     * onComplete / onError 透传给原始回调（负责发 onStreamComplete + 释放 CountDownLatch）。
     */
    private StreamingCallback buildRoundCallback(AgentContext context,
                                                  StringBuilder roundBuffer,
                                                  boolean[] isToolRound) {
        AgentEventPublisher publisher = context.getEventPublisher();
        StreamingCallback original = context.getStreamingCallback();

        return new StreamingCallback() {
            @Override
            public void onToken(String token) {
                // 如果尚未确定是工具轮，先缓冲
                // 工具轮结束后由 flushThinkingBuffer 处理；最终轮直接发 onToken
                if (!isToolRound[0]) {
                    // 最终轮：直接路由给 publisher（实时流式）
                    if (publisher != null) {
                        publisher.onToken(token);
                    }
                } else {
                    // 已知是工具轮：缓冲（稍后由 flushThinkingBuffer 统一发出）
                    roundBuffer.append(token);
                }
            }

            @Override
            public void onThinking(String thinkingToken) {
                // 深度思考模型的 reasoning_content：始终作为 thinkingToken 发出
                if (publisher != null) {
                    publisher.onThinkingToken(thinkingToken);
                }
            }

            @Override
            public void onComplete(String fullText) {
                if (original != null) {
                    original.onComplete(fullText);
                }
            }

            @Override
            public void onError(Throwable error) {
                if (original != null) {
                    original.onError(error);
                }
            }

            @Override
            public void onStart() {
                if (original != null) {
                    original.onStart();
                }
            }
        };
    }

    /**
     * 将工具调用轮缓冲的 token 作为思考内容统一发出，然后清空缓冲区
     */
    private void flushThinkingBuffer(AgentEventPublisher publisher, StringBuilder buffer) {
        if (publisher == null || buffer.isEmpty()) {
            buffer.setLength(0);
            return;
        }
        String content = buffer.toString();
        buffer.setLength(0);
        // 逐字符发出，保持流式体验
        for (int i = 0; i < content.length(); i++) {
            publisher.onThinkingToken(String.valueOf(content.charAt(i)));
        }
    }

    /**
     * 安全解析工具参数 JSON 字符串
     * 返回 Map（可序列化为 JSON），解析失败则返回原始字符串
     */
    private Object parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new HashMap<>();
        }
        try {
            return JSON.parseObject(arguments);
        } catch (Exception e) {
            return arguments;
        }
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000;
    }
}
