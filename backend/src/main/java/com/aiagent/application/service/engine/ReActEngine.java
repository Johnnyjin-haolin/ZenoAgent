package com.aiagent.application.service.engine;

import com.aiagent.application.service.action.ActionExecutor;
import com.aiagent.application.service.action.ActionResult;
import com.aiagent.application.service.action.AgentAction;
import com.aiagent.application.service.action.DirectResponseParams;
import com.aiagent.application.service.agent.AgentStateMachine;
import com.aiagent.shared.constant.AgentConstants;
import com.aiagent.domain.enums.AgentState;
import com.aiagent.application.model.AgentContext;
import com.aiagent.application.model.AgentKnowledgeResult;
import com.aiagent.api.dto.AgentEventData;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ReAct循环引擎
 * 实现Reasoning-Acting循环，让Agent能够自主思考、行动、观察和反思
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class ReActEngine {
    
    @Autowired
    private ThinkingEngine thinkingEngine;
    
    @Autowired
    private ActionExecutor actionExecutor;
    
    @Autowired
    private ObservationEngine observationEngine;
    
    @Autowired
    private ReflectionEngine reflectionEngine;
    
    @Autowired
    private AgentStateMachine stateMachine;
    
    /**
     * 最大迭代次数
     */
    private static final int MAX_ITERATIONS = AgentConstants.DEFAULT_MAX_ITERATIONS;

    /**
     * 快速响应时用于截断工具结果的最大长度
     */
    private static final int MAX_FAST_RESULT_CHARS = 1500;
    
    /**
     * 执行ReAct循环
     * 
     * @param goal 目标（用户请求）
     * @param context Agent上下文
     * @return 最终结果
     */
    public ActionResult execute(String goal, AgentContext context) {
        log.info("开始ReAct循环执行，目标: {}", goal);
        long totalStartNs = System.nanoTime();
        
        // 初始化状态机
        stateMachine.initialize(context, newState -> {
            log.debug("状态变更: {}", newState);
        });
        
        // 转换到思考状态
        stateMachine.transition(AgentState.THINKING);
        
        int iteration = 0;
        List<ActionResult> lastResults = null;
        
        while (iteration < MAX_ITERATIONS && !stateMachine.isTerminal()) {
            iteration++;
            long iterationStartNs = System.nanoTime();
            log.info("ReAct循环迭代 {}/{}", iteration, MAX_ITERATIONS);
            
            try {
                log.info("开始思考");
                // 1. 思考阶段（Think）
                sendProgressEvent(context, AgentConstants.EVENT_AGENT_PLANNING, "正在思考规划下一步...");
                long thinkStartNs = System.nanoTime();
                List<AgentAction> actions = think(goal, context, lastResults);
                int actionCount = actions == null ? 0 : actions.size();
                log.info("思考结束，返回 {} 个动作，耗时 {} ms", actionCount, elapsedMs(thinkStartNs));
                if (actions == null || actions.isEmpty()) {
                    log.warn("思考阶段未产生动作，结束循环");
                    stateMachine.transition(AgentState.FAILED);
                    break;
                }

                //todo 看下是否能把这个挪到反思那里
                // 检查是否有 COMPLETE action
                AgentAction completeAction = actions.stream()
                    .filter(a -> a.getType() == AgentAction.ActionType.COMPLETE)
                    .findFirst()
                    .orElse(null);
                if (completeAction != null) {
                    log.info("Agent决定完成任务");
                    stateMachine.transition(AgentState.COMPLETED);
                    return ActionResult.success("complete", "complete", 
                        "任务已完成: " + completeAction.getReasoning());
                }
                
                log.info("act开始");
                
                // 2. 行动阶段（Act）- 统一使用并行处理
                stateMachine.transition(AgentState.EXECUTING);
                long actStartNs = System.nanoTime();
                List<ActionResult> results = act(actions, context);
                lastResults = results;
                log.info("act结束，耗时 {} ms", elapsedMs(actStartNs));

                log.info("观察开始");

                //todo 这里看下是否能合并到观察阶段
                // 检查是否有 DIRECT_RESPONSE action（简单场景直接返回）
                AgentAction directResponseAction = actions.stream()
                        .filter(a -> a.getType() == AgentAction.ActionType.DIRECT_RESPONSE)
                        .findFirst()
                        .orElse(null);
                if (directResponseAction != null) {
                    log.info("识别为简单场景，执行直接返回响应");
                }
                
                // 3. 观察阶段（Observe）
                stateMachine.transition(AgentState.OBSERVING);
                sendProgressEvent(context, AgentConstants.EVENT_AGENT_OBSERVING, "正在观察执行结果...");
                long observeStartNs = System.nanoTime();

                log.info("观察结束，耗时 {} ms", elapsedMs(observeStartNs));
                boolean isEnd = observe(results, context);
                if (isEnd){
                    stateMachine.transition(AgentState.COMPLETED);
                    break;
                }
                log.info("本轮迭代耗时 {} ms", elapsedMs(iterationStartNs));
            } catch (Exception e) {
                log.error("ReAct循环执行异常", e);
                stateMachine.transition(AgentState.FAILED);
                return ActionResult.failure("react_loop", "react_loop", 
                    e.getMessage(), "EXCEPTION");
            }
        }

        //todo 这里的逻辑需要放到观察阶段
        // 检查是否因为达到最大迭代次数而结束
        if (iteration >= MAX_ITERATIONS) {
            log.warn("达到最大迭代次数，结束循环");
            stateMachine.transition(AgentState.FAILED);
            return ActionResult.failure("react_loop", "react_loop", 
                "达到最大迭代次数", "MAX_ITERATIONS");
        }
        
        // 返回最后的结果（取最后一个成功的结果，如果没有则取最后一个）
        if (lastResults != null && !lastResults.isEmpty()) {
            ActionResult lastSuccessResult = lastResults.stream()
                .filter(ActionResult::isSuccess)
                .reduce((first, second) -> second)
                .orElse(lastResults.get(lastResults.size() - 1));
            ActionResult finalResult = ensureUserFacingResult(goal, context, lastSuccessResult, lastResults);
            log.info("ReAct循环执行完成，总耗时 {} ms", elapsedMs(totalStartNs));
            return finalResult;
        }
        log.info("ReAct循环执行完成，总耗时 {} ms", elapsedMs(totalStartNs));
        return ActionResult.failure("react_loop", "react_loop", 
            "未产生有效结果", "NO_RESULT");
    }
    
    /**
     * 思考阶段：分析当前情况，决定下一步动作（支持多个动作）
     */
    private List<AgentAction> think(String goal, AgentContext context, List<ActionResult> lastResults) {
        log.debug("进入思考阶段");
        return thinkingEngine.think(goal, context, lastResults);
    }
    
    /**
     * 执行多个动作（统一使用并行处理）
     */
    private List<ActionResult> act(List<AgentAction> actions, AgentContext context) {
        log.debug("执行 {} 个动作（并行）", actions.size());
        return actionExecutor.executeParallel(actions, context);
    }
    
    /**
     * 观察阶段：观察行动结果，更新上下文
     */
    private boolean observe(List<ActionResult> results, AgentContext context) {
        log.debug("进入观察阶段，结果数量: {}", results.size());
        return observationEngine.observe(results, context);
    }

    
    /**
     * 使用流式API生成友好的完成总结
     * 当目标达成时，基于整个执行过程生成一个对用户友好的总结（流式输出）
     */
    private ActionResult generateCompletionSummaryWithStreaming(String goal, AgentContext context, List<ActionResult> lastResults) {
        log.info("使用流式API生成任务完成总结");
        
        try {
            // 构建总结提示词
            String summaryPrompt = buildCompletionSummaryPrompt(goal, context, lastResults);
            
            // 创建LLM_GENERATE动作（会使用流式输出）
            AgentAction summaryAction = AgentAction.llmGenerate(
                com.aiagent.application.service.action.LLMGenerateParams.builder()
                    .prompt(summaryPrompt)
                    .systemPrompt("你是一个智能助手。请根据任务执行过程，生成一个友好、完整的回复给用户。" +
                        "回复应该：1) 自然流畅，符合对话习惯；2) 包含关键信息；3) 避免过于技术化的描述。")
                    .build(),
                "生成任务完成总结"
            );
            
            // 执行LLM生成（会使用流式输出，通过context的streamingCallback发送到前端）
            return actionExecutor.execute(summaryAction, context);
            
        } catch (Exception e) {
            log.error("生成完成总结失败", e);
            return ActionResult.failure("summary", "summary", 
                "生成总结失败: " + e.getMessage(), "SUMMARY_ERROR");
        }
    }
    
    /**
     * 构建完成总结提示词
     */
    private String buildCompletionSummaryPrompt(String goal, AgentContext context, List<ActionResult> lastResults) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("用户的原始需求: ").append(goal).append("\n\n");
        
        // 添加工具调用历史
        if (context.getToolCallHistory() != null && !context.getToolCallHistory().isEmpty()) {
            prompt.append("我执行了以下操作:\n");
            for (Map<String, Object> call : context.getToolCallHistory()) {
                prompt.append("- 调用了工具: ").append(call.get("toolName"));
                if (call.containsKey("result")) {
                    Object toolResult = call.get("result");
                    String resultStr = toolResult != null ? toolResult.toString() : "";
                    if (resultStr.length() > 500) {
                        resultStr = resultStr.substring(0, 500) + "...";
                    }
                    prompt.append("，结果: ").append(resultStr);
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        // 添加RAG检索历史
        if (context.getRagRetrieveHistory() != null && !context.getRagRetrieveHistory().isEmpty()) {
            prompt.append("我检索了相关知识:\n");
            for (Map<String, Object> retrieve : context.getRagRetrieveHistory()) {
                prompt.append("- 查询: ").append(retrieve.get("query"));
                if (retrieve.containsKey("resultCount")) {
                    prompt.append("，找到 ").append(retrieve.get("resultCount")).append(" 条相关信息");
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        // 添加最终执行结果（合并所有结果）
        if (lastResults != null && !lastResults.isEmpty()) {
            prompt.append("最终执行结果（共 ").append(lastResults.size()).append(" 个动作）:\n");
            for (int i = 0; i < lastResults.size(); i++) {
                ActionResult result = lastResults.get(i);
                prompt.append("动作 ").append(i + 1).append(" (").append(result.getActionName()).append("): ");
                if (result.isSuccess() && result.getData() != null) {
                    String resultData = result.getData().toString();
                    // 限制长度
                    if (resultData.length() > 1000) {
                        resultData = resultData.substring(0, 1000) + "... (结果过长，已截断)";
                    }
                    prompt.append(resultData);
                } else {
                    prompt.append("执行失败: ").append(result.getError());
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("请根据以上信息，生成一个完整、友好的回复给用户。\n");
        prompt.append("要求:\n");
        prompt.append("1. 直接回答用户的问题或确认任务完成\n");
        prompt.append("2. 如果有具体数据或结果，清晰地呈现出来\n");
        prompt.append("3. 语气自然、友好，像人类助手一样交流\n");
        prompt.append("4. 不要说\"根据执行结果\"、\"我调用了工具\"等技术性语言\n");
        prompt.append("5. 如果结果是列表或表格，用清晰的格式展示\n");
        
        return prompt.toString();
    }
    
    /**
     * 发送进度事件到前端
     */
    private void sendProgressEvent(AgentContext context, String event, String message) {
        if (context.getEventPublisher() != null) {
            context.getEventPublisher().accept(
                AgentEventData.builder()
                    .event(event)
                    .message(message)
                    .build()
            );
        }
    }

    private ActionResult tryFastResponse(String goal, AgentContext context, List<AgentAction> actions,
                                         List<ActionResult> results) {
        if (!isFastResponseCandidate(actions, results)) {
            return null;
        }

        ActionResult toolResult = results.get(0);
        String resultText = extractResultText(toolResult);
        String prompt = buildFastResponsePrompt(goal, resultText);

        AgentAction fastAction = AgentAction.llmGenerate(
            com.aiagent.application.service.action.LLMGenerateParams.builder()
                .prompt(prompt)
                .systemPrompt("你是一个智能助手，请用简洁、友好的中文直接回答用户问题。不要提及工具调用或技术细节。")
                .build(),
            "单次工具查询结果快速生成回复"
        );

        stateMachine.transition(AgentState.EXECUTING);
        sendProgressEvent(context, AgentConstants.EVENT_AGENT_GENERATING, "正在生成回复...");
        ActionResult fastResult = actionExecutor.execute(fastAction, context);
        // 快速回复执行完成后回到OBSERVING，确保后续可转换到COMPLETED
        stateMachine.transition(AgentState.OBSERVING);
        return fastResult;
    }

    private boolean isFastResponseCandidate(List<AgentAction> actions, List<ActionResult> results) {
        if (actions == null || results == null) {
            return false;
        }
        if (actions.size() != 1 || results.size() != 1) {
            return false;
        }
        AgentAction action = actions.get(0);
        ActionResult result = results.get(0);
        return action.getType() == AgentAction.ActionType.TOOL_CALL && result.isSuccess();
    }

    private String extractResultText(ActionResult result) {
        String resultText = "";
        if (result.getMetadata() != null) {
            Object metadataResult = result.getMetadata().get("resultStr");
            if (metadataResult instanceof String) {
                resultText = (String) metadataResult;
            }
        }
        if (resultText == null || resultText.isEmpty()) {
            resultText = result.getData() != null ? result.getData().toString() : "";
        }
        if (resultText.length() > MAX_FAST_RESULT_CHARS) {
            resultText = resultText.substring(0, MAX_FAST_RESULT_CHARS) + "...";
        }
        return resultText;
    }

    private String buildFastResponsePrompt(String goal, String resultText) {
        String safeResult = (resultText == null || resultText.isEmpty()) ? "（无数据）" : resultText;
        return "用户问题: " + goal + "\n\n" +
            "工具返回结果:\n" + safeResult + "\n\n" +
            "请直接回答用户问题。如果结果为空/无资源，请直接说明。";
    }

    private ActionResult ensureUserFacingResult(String goal, AgentContext context, ActionResult candidate,
                                                List<ActionResult> results) {
        if (candidate == null) {
            return null;
        }
        String actionType = candidate.getActionType();
        if (actionType == null) {
            return candidate;
        }
        String normalized = actionType.toLowerCase();
        if ("llm_generate".equals(normalized) || "direct_response".equals(normalized) || "complete".equals(normalized)) {
            return candidate;
        }
        if (!"tool_call".equals(normalized) && !"rag_retrieve".equals(normalized)) {
            return candidate;
        }
        ActionResult response = buildUserFacingResponse(goal, context, candidate);
        return response != null ? response : candidate;
    }

    private ActionResult buildUserFacingResponse(String goal, AgentContext context, ActionResult candidate) {
        if ("rag_retrieve".equalsIgnoreCase(candidate.getActionType())) {
            ActionResult direct = buildRagEmptyResponseIfNeeded(context, candidate);
            if (direct != null) {
                return direct;
            }
        }

        String resultText = extractResultText(candidate);
        if (isEmptyToolResult(candidate.getActionType(), resultText)) {
            return executeDirectResponse(context, "当前未查询到相关信息。你可以补充更多条件后再试。");
        }

        String prompt = buildFinalResponsePrompt(goal, resultText, candidate.getActionType());
        AgentAction action = AgentAction.llmGenerate(
            com.aiagent.application.service.action.LLMGenerateParams.builder()
                .prompt(prompt)
                .systemPrompt("你是一个智能助手，请用简洁、友好的中文直接回答用户问题。不要提及工具调用或技术细节。")
                .build(),
            "基于执行结果生成用户回复"
        );
        stateMachine.transition(AgentState.EXECUTING);
        sendProgressEvent(context, AgentConstants.EVENT_AGENT_GENERATING, "正在生成回复...");
        ActionResult result = actionExecutor.execute(action, context);
        stateMachine.transition(AgentState.OBSERVING);
        return result;
    }

    private ActionResult buildRagEmptyResponseIfNeeded(AgentContext context, ActionResult candidate) {
        Object data = candidate.getData();
        if (data instanceof AgentKnowledgeResult) {
            AgentKnowledgeResult knowledgeResult = (AgentKnowledgeResult) data;
            if (knowledgeResult.isEmpty() || (knowledgeResult.getTotalCount() != null && knowledgeResult.getTotalCount() == 0)) {
                return executeDirectResponse(context, "未检索到相关知识库内容，请补充设备、时间范围或告警信息后再试。");
            }
        }
        return null;
    }

    private ActionResult executeDirectResponse(AgentContext context, String content) {
        AgentAction action = AgentAction.directResponse(
            DirectResponseParams.builder()
                .content(content)
                .streaming(true)
                .build(),
            "基于执行结果直接回复"
        );
        stateMachine.transition(AgentState.EXECUTING);
        sendProgressEvent(context, AgentConstants.EVENT_AGENT_GENERATING, "正在生成回复...");
        ActionResult result = actionExecutor.execute(action, context);
        stateMachine.transition(AgentState.OBSERVING);
        return result;
    }

    private boolean isEmptyToolResult(String actionType, String resultText) {
        if (resultText == null || resultText.isEmpty()) {
            return true;
        }
        String normalized = resultText.replaceAll("\\s+", "").toLowerCase();
        if (normalized.contains("\"resources\":[]") || normalized.contains("\"resources\":null")) {
            return true;
        }
        if (normalized.contains("\"totalcount\":0") || normalized.contains("\"count\":0")) {
            return true;
        }
        return "rag_retrieve".equalsIgnoreCase(actionType) && (normalized.contains("\"documents\":[]") || normalized.contains("\"totalcount\":0"));
    }

    private String buildFinalResponsePrompt(String goal, String resultText, String actionType) {
        String safeResult = (resultText == null || resultText.isEmpty()) ? "（无数据）" : resultText;
        if (safeResult.length() > MAX_FAST_RESULT_CHARS) {
            safeResult = safeResult.substring(0, MAX_FAST_RESULT_CHARS) + "...";
        }
        return "用户问题: " + goal + "\n\n" +
            "执行结果类型: " + actionType + "\n" +
            "执行结果:\n" + safeResult + "\n\n" +
            "请基于以上结果，用简洁的中文回答用户问题。";
    }

    private long elapsedMs(long startNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    }
}

