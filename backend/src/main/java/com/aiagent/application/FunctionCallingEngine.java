package com.aiagent.application;

import com.aiagent.common.constant.AgentConstants;
import com.aiagent.common.enums.AgentMode;
import com.aiagent.common.enums.AgentState;
import com.aiagent.domain.agent.AgentDefinition;
import com.aiagent.domain.agent.AgentDefinitionLoader;
import com.aiagent.domain.llm.SimpleLLMChatHandler;
import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.model.bo.AgentExecutionResult;
import com.aiagent.domain.model.bo.AgentRuntimeConfig;
import com.aiagent.domain.model.bo.ExecutionProcessRecord;
import com.aiagent.domain.model.bo.ExecutionProcessRecord.Iteration;
import com.aiagent.domain.model.bo.ExecutionProcessRecord.Step;
import com.aiagent.domain.skill.AgentSkill;
import com.aiagent.domain.skill.AgentSkillService;
import com.aiagent.domain.skill.SkillTreeNode;
import com.aiagent.domain.tool.ToolRegistry;
import com.aiagent.domain.tool.todo.TodoItem;
import com.aiagent.api.dto.McpToolInfo;
import com.aiagent.domain.mcp.McpServerService;
import com.aiagent.infrastructure.external.mcp.ClientToolCallManager;
import com.aiagent.infrastructure.external.mcp.ToolConfirmationDecision;
import com.aiagent.infrastructure.external.mcp.ToolConfirmationManager;
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

import java.util.*;

/**
 * 基于 LangChain4j 原生 Function Calling 的标准 Agent 推理引擎（默认引擎）
 *
 * <p>适用于支持 Function Calling 的主流模型（GPT-4、Claude、DeepSeek R1、Qwen3 等）。
 * 通过 {@code toolSpecifications} 将工具定义以结构化参数传递给 LLM，
 * 依据 {@code FinishReason.TOOL_EXECUTION} 驱动推理循环，无需 Prompt 解析。
 *
 * <p>支持两种执行模式：
 * <ul>
 *   <li>{@link AgentMode#AUTO}   - 工具立即执行，无需用户确认</li>
 *   <li>{@link AgentMode#MANUAL} - 每个工具执行前发出确认请求，等待用户批准/拒绝，
 *       通过 {@link ToolConfirmationManager} + Redis BlockingQueue 实现阻塞等待</li>
 * </ul>
 *
 * <p>最大工具轮数通过 {@code context.getMaxToolRounds()} 读取（委托到 {@link AgentRuntimeConfig}），
 * 默认值 8，可在 {@link AgentDefinition.ContextConfig} 中配置。
 */
@Slf4j
@Component
public class FunctionCallingEngine implements AgentEngine {

    /** MANUAL 模式下等待用户确认的超时时间（毫秒），5 分钟 */
    private static final long MANUAL_CONFIRM_TIMEOUT_MS = 5 * 60_000L;

    @Autowired
    private SimpleLLMChatHandler llmChatHandler;

    @Autowired
    private AgentDefinitionLoader agentDefinitionLoader;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolConfirmationManager toolConfirmationManager;

    @Autowired
    private AgentSkillService agentSkillService;

    @Autowired
    private ClientToolCallManager clientToolCallManager;

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

        // 最大工具轮数（从 context 读取，由 AgentContextService 注入）
        int maxToolRounds = context.getMaxToolRounds();

        // 是否启用 MANUAL 手动确认模式
        boolean manualMode = AgentMode.MANUAL.equals(context.getMode());

        // 判断是否启用渐进式加载
        boolean progressiveMode = toolRegistry.isProgressiveMode(agentDef);
        log.info("执行模式: {}, 渐进式工具加载: {}",
            manualMode ? "MANUAL" : "AUTO",
            progressiveMode ? "已启用" : "未启用");

        // 初始化 messages
        List<ChatMessage> messages = buildMessages(agentDef, context, progressiveMode);

        // 初始化 toolSpecs（GLOBAL 工具），运行时 systemTools 优先覆盖 AgentDef
        List<ToolSpecification> toolSpecs = resolveInitialToolSpecs(agentDef, context, progressiveMode);

        // PERSONAL MCP 工具：由前端 prefetch 后随 AgentRequest 上传真实 schema，
        // 后端直接构造真实 ToolSpecification，不再注入占位假工具
        List<com.aiagent.api.dto.PersonalMcpToolSchema> personalSchemas = context.getPersonalMcpTools();
        java.util.Map<String, String> personalToolServerId = toolRegistry.buildPersonalToolMapFromSchemas(personalSchemas);
        if (personalSchemas != null && !personalSchemas.isEmpty()) {
            toolSpecs = toolRegistry.appendPersonalToolSpecs(toolSpecs, personalSchemas);
            log.info("注入 PERSONAL MCP 真实工具 schema: {} 个", personalSchemas.size());
        }

        int toolRound = 0;

        ExecutionProcessRecord processRecord = new ExecutionProcessRecord();
        processRecord.setIterations(new ArrayList<>());

        try {
            while (toolRound <= maxToolRounds) {
                log.info("Function Calling 循环第 {} 轮", toolRound);

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
                    isToolRound[0] = true;
                    String thinkingContent = roundBuffer.toString();
                    flushThinkingBuffer(publisher, roundBuffer);

                    toolRound++;
                    List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();

                    long iterStartNs = System.nanoTime();
                    Iteration iteration = new Iteration();
                    iteration.setIterationNumber(toolRound);
                    iteration.setSteps(new ArrayList<>());

                    if (!thinkingContent.isBlank()) {
                        iteration.getSteps().add(Step.builder()
                            .type("thinking")
                            .content(thinkingContent)
                            .build());
                    }

                    for (ToolExecutionRequest toolRequest : toolRequests) {
                        iteration.getSteps().add(Step.builder()
                            .type("tool_call")
                            .toolName(toolRequest.name())
                            .toolParams(toolRequest.arguments())
                            .build());

                        ToolExecutionResultMessage resultMsg;

                        if (manualMode) {
                            // ── MANUAL 模式：先通知前端等待确认，再阻塞等待用户决策 ─────
                            String toolExecutionId = UUID.randomUUID().toString();
                            toolConfirmationManager.register(toolExecutionId);

                            if (publisher != null) {
                                publisher.onToolCall(
                                    toolRequest.name(),
                                    parseArguments(toolRequest.arguments()),
                                    true,
                                    toolExecutionId
                                );
                            }

                            log.info("[MANUAL] 等待用户确认: toolName={}, toolExecutionId={}",
                                toolRequest.name(), toolExecutionId);

                            ToolConfirmationDecision decision =
                                toolConfirmationManager.waitForDecision(toolExecutionId, MANUAL_CONFIRM_TIMEOUT_MS);

                            log.info("[MANUAL] 用户决策: toolName={}, decision={}",
                                toolRequest.name(), decision);

                            if (ToolConfirmationDecision.APPROVED.equals(decision)) {
                                long toolStart = System.nanoTime();
                                resultMsg = toolRegistry.execute(toolRequest, context);
                                long toolDurationMs = (System.nanoTime() - toolStart) / 1_000_000;

                                messages.add(resultMsg);
                                context.addMessage(resultMsg);

                                boolean isError = resultMsg.text() != null && resultMsg.text().startsWith("[ERROR]");
                                iteration.getSteps().add(Step.builder()
                                    .type("tool_result")
                                    .toolName(toolRequest.name())
                                    .toolResult(resultMsg.text())
                                    .toolDurationMs(toolDurationMs)
                                    .error(isError)
                                    .errorMessage(isError ? resultMsg.text() : null)
                                    .build());

                                if (publisher != null) {
                                    publisher.onToolResult(
                                        toolRequest.name(),
                                        resultMsg.text(),
                                        toolDurationMs,
                                        isError ? resultMsg.text() : null
                                    );
                                }
                            } else {
                                // 拒绝或超时：构造拒绝结果，让 LLM 感知并重新规划
                                String rejectMsg = ToolConfirmationDecision.TIMEOUT.equals(decision)
                                    ? "用户未在规定时间内确认，工具执行已取消"
                                    : "用户拒绝了工具执行请求";
                                resultMsg = ToolExecutionResultMessage.builder()
                                    .id(toolRequest.id())
                                    .toolName(toolRequest.name())
                                    .text(rejectMsg)
                                    .isError(false)
                                    .build();

                                messages.add(resultMsg);
                                context.addMessage(resultMsg);

                                iteration.getSteps().add(Step.builder()
                                    .type("tool_result")
                                    .toolName(toolRequest.name())
                                    .toolResult(rejectMsg)
                                    .toolDurationMs(0L)
                                    .error(false)
                                    .build());

                                if (publisher != null) {
                                    publisher.onToolResult(toolRequest.name(), rejectMsg, 0L, null);
                                }
                            }

                        } else {
                            // ── AUTO 模式：判断是否为 PERSONAL 工具 ──────────────────────

                            String personalServerId = toolRegistry.getPersonalServerId(
                                toolRequest.name(), personalToolServerId);

                            if (personalServerId != null && publisher != null) {
                                // ── PERSONAL MCP：通过 SSE 下发给浏览器执行 ──────────────
                                java.util.concurrent.CompletableFuture<String> future =
                                    new java.util.concurrent.CompletableFuture<>();
                                String callId = clientToolCallManager.newCall(future);

                                publisher.onPersonalToolCall(
                                    callId,
                                    toolRequest.name(),
                                    personalServerId,
                                    parseArguments(toolRequest.arguments())
                                );

                                log.info("[PERSONAL] SSE 下发工具调用: toolName={}, callId={}",
                                    toolRequest.name(), callId);

                                long toolStart = System.nanoTime();
                                String resultText;
                                boolean isError = false;
                                try {
                                    resultText = clientToolCallManager.waitForResult(callId);
                                } catch (Exception ex) {
                                    resultText = "[ERROR] 客户端工具执行失败: " + ex.getMessage();
                                    isError = true;
                                }
                                long toolDurationMs = (System.nanoTime() - toolStart) / 1_000_000;

                                resultMsg = ToolExecutionResultMessage.builder()
                                    .id(toolRequest.id())
                                    .toolName(toolRequest.name())
                                    .text(resultText)
                                    .isError(isError)
                                    .build();

                                messages.add(resultMsg);
                                context.addMessage(resultMsg);

                                iteration.getSteps().add(Step.builder()
                                    .type("tool_result")
                                    .toolName(toolRequest.name())
                                    .toolResult(resultText)
                                    .toolDurationMs(toolDurationMs)
                                    .error(isError)
                                    .errorMessage(isError ? resultText : null)
                                    .build());

                                if (publisher != null) {
                                    publisher.onToolResult(
                                        toolRequest.name(), resultText, toolDurationMs,
                                        isError ? resultText : null);
                                }

                            } else {
                                // ── GLOBAL MCP / 系统工具：直接在服务端执行 ──────────────
                                if (publisher != null) {
                                    publisher.onToolCall(toolRequest.name(),
                                        parseArguments(toolRequest.arguments()));
                                }

                                long toolStart = System.nanoTime();
                                resultMsg = toolRegistry.execute(toolRequest, context);
                                long toolDurationMs = (System.nanoTime() - toolStart) / 1_000_000;

                                messages.add(resultMsg);
                                context.addMessage(resultMsg);

                                boolean isError = resultMsg.text() != null
                                    && resultMsg.text().startsWith("[ERROR]");
                                iteration.getSteps().add(Step.builder()
                                    .type("tool_result")
                                    .toolName(toolRequest.name())
                                    .toolResult(resultMsg.text())
                                    .toolDurationMs(toolDurationMs)
                                    .error(isError)
                                    .errorMessage(isError ? resultMsg.text() : null)
                                    .build());

                                if (publisher != null) {
                                    publisher.onToolResult(
                                        toolRequest.name(),
                                        resultMsg.text(),
                                        toolDurationMs,
                                        isError ? resultMsg.text() : null
                                    );
                                }
                            }
                        }
                        log.info("迭代执行结果：{}", resultMsg);
                    }

                    iteration.setDurationMs(elapsedMs(iterStartNs));
                    processRecord.getIterations().add(iteration);

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

                log.info("Function Calling 循环结束，共执行 {} 轮工具调用", toolRound);
                break;
            }

            if (toolRound > maxToolRounds) {
                log.warn("工具调用轮次超限（max={}），强制终止推理循环", maxToolRounds);
                long durationMs = elapsedMs(startNs);
                return AgentExecutionResult.failure("工具调用轮次超限", "MAX_ITERATIONS_EXCEEDED",
                    context.getMessages(), toolRound, durationMs, AgentState.FAILED);
            }

            long durationMs = elapsedMs(startNs);
            processRecord.setTotalDurationMs(durationMs);
            context.setExecutionProcess(processRecord);

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

    private List<ChatMessage> buildMessages(AgentDefinition agentDef, AgentContext context, boolean progressiveMode) {
        List<ChatMessage> messages = new ArrayList<>();

        if (agentDef != null && agentDef.getSystemPrompt() != null && !agentDef.getSystemPrompt().isEmpty()) {
            String systemPrompt = agentDef.getSystemPrompt();

            if (progressiveMode) {
                String toolSummary = toolRegistry.buildMcpToolSummary(agentDef);
                if (!toolSummary.isEmpty()) {
                    systemPrompt += toolSummary;
                }
            }

            String skillSection = buildSkillSection(agentDef);
            if (!skillSection.isEmpty()) {
                systemPrompt += skillSection;
            }

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
     * 构建 Skill 摘要段落，注入到 System Prompt
     * <p>遍历 Agent 的 skillTree，收集所有 enabled=true 的叶节点 AgentSkill 摘要。
     */
    private String buildSkillSection(AgentDefinition agentDef) {
        if (agentDef.getSkillTree() == null || agentDef.getSkillTree().isEmpty()) {
            return "";
        }

        List<SkillTreeNode> enabledLeaves = new ArrayList<>();
        collectEnabledLeafNodes(agentDef.getSkillTree(), enabledLeaves);
        if (enabledLeaves.isEmpty()) {
            return "";
        }

        // 收集所有 skillId，一次批量查询，避免 N 次单查
        List<String> skillIds = enabledLeaves.stream()
            .map(SkillTreeNode::getSkillId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(java.util.stream.Collectors.toList());

        if (skillIds.isEmpty()) {
            return "";
        }

        Map<String, AgentSkill> skillMap = agentSkillService.getByIdMap(skillIds);

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 可用技能列表\n");
        sb.append("如需查看某条技能的完整内容，请调用 `system_load_skill` 工具并传入技能 ID。\n\n");

        for (SkillTreeNode node : enabledLeaves) {
            if (node.getSkillId() == null) {
                continue;
            }
            AgentSkill skill = skillMap.get(node.getSkillId());
            if (skill == null) {
                continue;
            }
            sb.append("- [").append(skill.getId()).append("] **").append(skill.getName()).append("**: ");
            sb.append(skill.getSummary()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 递归收集 enabled=true 的叶节点（有 skillId 的节点）
     */
    private void collectEnabledLeafNodes(List<SkillTreeNode> nodes, List<SkillTreeNode> result) {
        if (nodes == null) {
            return;
        }
        for (SkillTreeNode node : nodes) {
            if (!node.isEnabled()) {
                continue;
            }
            if (node.getSkillId() != null) {
                result.add(node);
            } else {
                collectEnabledLeafNodes(node.getChildren(), result);
            }
        }
    }

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
                    if (a.getStatus() == TodoItem.TodoStatus.pending) {
                        return -1;
                    }
                    if (b.getStatus() == TodoItem.TodoStatus.pending) {
                        return 1;
                    }
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

    private List<ToolSpecification> resolveInitialToolSpecs(
            AgentDefinition agentDef, AgentContext context, boolean progressiveMode) {
        List<ToolSpecification> result = new ArrayList<>();

        if (agentDef == null || agentDef.getTools() == null) {
            return result;
        }

        // 若 context 中有运行时 systemTools（前端对话级覆盖），临时构造一个覆盖后的 AgentDef 副本
        AgentDefinition effectiveDef = agentDef;
        List<String> runtimeSystemTools = context != null ? context.getSystemTools() : null;
        if (runtimeSystemTools != null) {
            AgentDefinition override = new AgentDefinition();
            override.setId(agentDef.getId());
            override.setName(agentDef.getName());
            override.setSystemPrompt(agentDef.getSystemPrompt());
            override.setContextConfig(agentDef.getContextConfig());
            override.setRagConfig(agentDef.getRagConfig());
            override.setSkillTree(agentDef.getSkillTree());
            AgentDefinition.ToolsConfig toolsCopy = new AgentDefinition.ToolsConfig();
            toolsCopy.setMcpServers(agentDef.getTools().getMcpServers());
            toolsCopy.setSystemTools(runtimeSystemTools);
            toolsCopy.setKnowledgeIds(agentDef.getTools().getKnowledgeIds());
            override.setTools(toolsCopy);
            effectiveDef = override;
            log.debug("运行时 systemTools 覆盖 AgentDef: {}", runtimeSystemTools);
        }

        List<ToolSpecification> allSpecs = toolRegistry.resolveToolSpecifications(effectiveDef);

        allSpecs.stream()
            .filter(spec -> spec.name().startsWith("system_"))
            .filter(spec -> {
                // 非渐进式模式下，system_resolve_tools 无意义，不注入，避免 LLM 误调用
                return progressiveMode || !"system_resolve_tools".equals(spec.name());
            })
            .forEach(result::add);

        if (!progressiveMode) {
            allSpecs.stream()
                .filter(spec -> !spec.name().startsWith("system_"))
                .forEach(result::add);
        }

        log.info("初始化工具列表，渐进式模式={}, toolSpecs数量={}", progressiveMode, result.size());
        return result;
    }

    private StreamingCallback buildRoundCallback(AgentContext context,
                                                  StringBuilder roundBuffer,
                                                  boolean[] isToolRound) {
        AgentEventPublisher publisher = context.getEventPublisher();
        StreamingCallback original = context.getStreamingCallback();

        return new StreamingCallback() {
            @Override
            public void onToken(String token) {
                if (!isToolRound[0]) {
                    if (publisher != null) {
                        publisher.onToken(token);
                    }
                } else {
                    roundBuffer.append(token);
                }
            }

            @Override
            public void onThinking(String thinkingToken) {
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

    private void flushThinkingBuffer(AgentEventPublisher publisher, StringBuilder buffer) {
        if (publisher == null || buffer.isEmpty()) {
            buffer.setLength(0);
            return;
        }
        String content = buffer.toString();
        buffer.setLength(0);
        for (int i = 0; i < content.length(); i++) {
            publisher.onThinkingToken(String.valueOf(content.charAt(i)));
        }
    }

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
