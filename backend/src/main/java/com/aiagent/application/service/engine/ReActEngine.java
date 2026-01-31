package com.aiagent.application.service.engine;

import com.aiagent.application.service.action.ActionExecutor;
import com.aiagent.application.service.action.ActionResult;
import com.aiagent.application.service.action.AgentAction;
import com.aiagent.application.service.agent.AgentStateMachine;
import com.aiagent.domain.enums.ActionType;
import com.aiagent.shared.constant.AgentConstants;
import com.aiagent.domain.enums.AgentState;
import com.aiagent.application.model.AgentContext;
import com.aiagent.api.dto.AgentEventData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    //todo 最终结果不应该复用ActionResult，最终结果应该包含所有发给用户的对话，这样在外层就可以存储持久化下来
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
                actions.stream()
                        .filter(a -> a.getType() == ActionType.DIRECT_RESPONSE)
                        .findFirst().ifPresent(directResponseAction -> log.info("识别为简单场景，执行直接返回响应"));

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
        return action.getType() == ActionType.TOOL_CALL && result.isSuccess();
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


    private long elapsedMs(long startNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    }
}

