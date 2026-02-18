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
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
     * 执行ReAct循环
     * 
     * @param goal 目标（用户请求）
     * @param context Agent上下文
     * @return 最终结果，包含所有对话消息
     */
    public ReActExecutionResult execute(String goal, AgentContext context) {
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
            
            // 发送迭代开始事件
            sendIterationStartEvent(context, iteration);
            
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
                    long totalDurationMs = elapsedMs(totalStartNs);
                    List<ChatMessage> allMessages = context.getMessages();
                    return ReActExecutionResult.failure(
                        "思考阶段未产生动作",
                        "NO_ACTIONS",
                        allMessages,
                        iteration,
                        totalDurationMs,
                        AgentState.FAILED
                    );
                }

                log.info("act开始");
                
                // 2. 行动阶段（Act）- 统一使用并行处理
                stateMachine.transition(AgentState.EXECUTING);
                long actStartNs = System.nanoTime();
                List<ActionResult> results = act(actions, context);
                lastResults = results;
                log.info("act结束，耗时 {} ms", elapsedMs(actStartNs));

                log.info("观察开始");

                // 3. 观察阶段（Observe）
                stateMachine.transition(AgentState.OBSERVING);
                sendProgressEvent(context, AgentConstants.EVENT_AGENT_OBSERVING, "正在观察执行结果...");
                long observeStartNs = System.nanoTime();

                ObservationResult observationResult = observationEngine.observe(
                    results, 
                    context, 
                    iteration, 
                    MAX_ITERATIONS, 
                    totalStartNs
                );
                
                log.info("观察结束，耗时 {} ms", elapsedMs(observeStartNs));
                
                // 计算本轮迭代耗时
                long iterationDurationMs = elapsedMs(iterationStartNs);
                
                // 根据观察结果决定是否结束
                if (observationResult.isShouldTerminate()) {
                    log.info("观察阶段判断应该结束循环，原因: {}", 
                        observationResult.getTerminationReason());
                        
                    // 发送迭代结束事件（终止）
                    sendIterationEndEvent(context, iteration, observationResult, iterationDurationMs);
                        
                    // 根据终止原因设置状态
                    if (observationResult.getTerminationReason() == 
                        ObservationResult.TerminationReason.COMPLETED) {
                        stateMachine.transition(AgentState.COMPLETED);
                    } else {
                        stateMachine.transition(AgentState.FAILED);
                    }
                    
                    return observationResult.getExecutionResult();
                }
                
                // 发送迭代结束事件（继续）
                sendIterationEndEvent(context, iteration, observationResult, iterationDurationMs);
                
                log.info("本轮迭代耗时 {} ms", iterationDurationMs);
            } catch (Exception e) {
                log.error("ReAct循环执行异常", e);
                stateMachine.transition(AgentState.FAILED);
                // 【关键1】发送错误事件
                try {
                    sendProgressEvent(context, AgentConstants.EVENT_AGENT_ERROR,
                        String.format("第%d轮推理执行失败: %s", iteration, e.getMessage()));
                } catch (Exception sendErrorEx) {
                    log.error("发送错误事件失败", sendErrorEx);
                }

                long totalDurationMs = elapsedMs(totalStartNs);
                AgentState finalState = stateMachine.getCurrentState();
                // 即使失败，也返回已有的消息
                List<ChatMessage> allMessages = context.getMessages();
                return ReActExecutionResult.builder()
                    .success(false)
                    .error(e.getMessage())
                    .errorType("EXCEPTION")
                    .messages(allMessages != null ? allMessages : new ArrayList<>())
                    .iterations(iteration)
                    .totalDurationMs(totalDurationMs)
                    .finalState(finalState != null ? finalState : AgentState.FAILED)
                    .metadata(new java.util.HashMap<>())
                    .build();
            }
        }
        
        // 如果循环正常退出但没有在观察阶段返回结果，构建默认结果
        // （这种情况理论上不应该发生，因为观察阶段会判断最大迭代次数）
        log.warn("循环异常退出，构建默认失败结果");
        long totalDurationMs = elapsedMs(totalStartNs);
        List<ChatMessage> allMessages = context.getMessages();
        return ReActExecutionResult.failure(
            "循环异常退出",
            "UNEXPECTED_EXIT",
            allMessages,
            iteration,
            totalDurationMs,
            stateMachine.getCurrentState()
        );
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
    
    /**
     * 发送迭代开始事件
     */
    private void sendIterationStartEvent(AgentContext context, int iterationNumber) {
        if (context != null && context.getEventPublisher() != null) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("iterationNumber", iterationNumber);
            data.put("message", "开始第 " + iterationNumber + " 轮推理");
            
            context.getEventPublisher().accept(
                AgentEventData.builder()
                    .event(AgentConstants.EVENT_ITERATION_START)
                    .message("开始第 " + iterationNumber + " 轮推理")
                    .data(data)
                    .conversationId(context.getConversationId())
                    .build()
            );
            
            log.debug("发送迭代开始事件：第 {} 轮", iterationNumber);
        }
    }
    
    /**
     * 发送迭代结束事件
     */
    private void sendIterationEndEvent(AgentContext context, int iterationNumber, 
                                       ObservationResult observationResult, long durationMs) {
        if (context != null && context.getEventPublisher() != null) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("iterationNumber", iterationNumber);
            data.put("shouldContinue", !observationResult.isShouldTerminate());
            data.put("durationMs", durationMs);
            
            String terminationReason = null;
            String message;
            
            if (observationResult.isShouldTerminate()) {
                terminationReason = observationResult.getTerminationReason() != null 
                    ? observationResult.getTerminationReason().name() 
                    : null;
                data.put("terminationReason", terminationReason);
                
                // 将 terminationReason 作为消息发送给前端，由前端进行国际化处理
                message = terminationReason != null ? terminationReason : "UNKNOWN";
            } else {
                message = "CONTINUE";
            }
            
            data.put("message", message);
            
            context.getEventPublisher().accept(
                AgentEventData.builder()
                    .event(AgentConstants.EVENT_ITERATION_END)
                    .message(message)
                    .data(data)
                    .conversationId(context.getConversationId())
                    .build()
            );
            
            log.debug("发送迭代结束事件：第 {} 轮，shouldContinue={}", 
                iterationNumber, !observationResult.isShouldTerminate());
        }
    }


    
    private long elapsedMs(long startNs) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    }
}

