package com.aiagent.service;

import com.aiagent.constant.AgentConstants;
import com.aiagent.enums.AgentState;
import com.aiagent.vo.AgentContext;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
     * 执行ReAct循环
     * 
     * @param goal 目标（用户请求）
     * @param context Agent上下文
     * @return 最终结果
     */
    public ActionResult execute(String goal, AgentContext context) {
        log.info("开始ReAct循环执行，目标: {}", goal);
        
        // 初始化状态机
        stateMachine.initialize(context, newState -> {
            log.debug("状态变更: {}", newState);
        });
        
        // 转换到思考状态
        stateMachine.transition(AgentState.THINKING);
        
        int iteration = 0;
        ActionResult lastResult = null;
        
        while (iteration < MAX_ITERATIONS && !stateMachine.isTerminal()) {
            iteration++;
            log.info("ReAct循环迭代 {}/{}", iteration, MAX_ITERATIONS);
            
            try {
                log.info("开始思考");
                // 1. 思考阶段（Think）
                AgentAction action = think(goal, context, lastResult);
                log.info("思考结束，{}", JSON.toJSONString(action));
                if (action == null) {
                    log.warn("思考阶段未产生动作，结束循环");
                    stateMachine.transition(AgentState.FAILED);
                    break;
                }
                
                // 检查是否完成
                if (action.getType() == AgentAction.ActionType.COMPLETE) {
                    log.info("Agent决定完成任务");
                    stateMachine.transition(AgentState.COMPLETED);
                    return ActionResult.success("complete", "complete", 
                        "任务已完成: " + action.getReasoning());
                }
                log.info("act开始");
                
                // 2. 行动阶段（Act）
                stateMachine.transition(AgentState.EXECUTING);
                ActionResult result = act(action, context);
                lastResult = result;

                log.info("观察开始");
                
                // 3. 观察阶段（Observe）
                stateMachine.transition(AgentState.OBSERVING);
                observe(result, context);

                log.info("反思开始");
                
                // 4. 反思阶段（Reflect）
                stateMachine.transition(AgentState.REFLECTING);
                ReflectionEngine.ReflectionResult reflection = reflect(result, context, goal);
                log.info("反思结束，{}", JSON.toJSONString(reflection));
                // 根据反思结果决定下一步
                if (reflection.isGoalAchieved()) {
                    log.info("反思结果：目标已达成");
                    stateMachine.transition(AgentState.COMPLETED);
                    // 目标达成时，直接返回result的数据，而不是reflection的summary
                    // summary包含"目标已达成: "前缀，不应该发给用户
                    return ActionResult.success("complete", "complete", 
                        result.getData());
                } else if (reflection.isShouldRetry()) {
                    log.info("反思结果：需要重试，原因: {}", reflection.getRetryReason());
                    stateMachine.transition(AgentState.THINKING);
                } else if (reflection.isShouldContinue()) {
                    log.info("反思结果：继续执行下一步");
                    stateMachine.transition(AgentState.THINKING);
                } else {
                    log.warn("反思结果：无法继续，结束循环");
                    stateMachine.transition(AgentState.FAILED);
                    break;
                }
                
            } catch (Exception e) {
                log.error("ReAct循环执行异常", e);
                stateMachine.transition(AgentState.FAILED);
                return ActionResult.failure("react_loop", "react_loop", 
                    e.getMessage(), "EXCEPTION");
            }
        }
        
        // 检查是否因为达到最大迭代次数而结束
        if (iteration >= MAX_ITERATIONS) {
            log.warn("达到最大迭代次数，结束循环");
            stateMachine.transition(AgentState.FAILED);
            return ActionResult.failure("react_loop", "react_loop", 
                "达到最大迭代次数", "MAX_ITERATIONS");
        }
        
        // 返回最后的结果
        return lastResult != null ? lastResult : 
            ActionResult.failure("react_loop", "react_loop", 
                "未产生有效结果", "NO_RESULT");
    }
    
    /**
     * 思考阶段：分析当前情况，决定下一步动作
     */
    private AgentAction think(String goal, AgentContext context, ActionResult lastResult) {
        log.debug("进入思考阶段");
        return thinkingEngine.think(goal, context, lastResult);
    }
    
    /**
     * 行动阶段：执行选定的动作
     */
    private ActionResult act(AgentAction action, AgentContext context) {
        log.debug("进入行动阶段，动作: {}", action.getName());
        return actionExecutor.execute(action, context);
    }
    
    /**
     * 观察阶段：观察行动结果，更新上下文
     */
    private void observe(ActionResult result, AgentContext context) {
        log.debug("进入观察阶段，结果: {}", result.isSuccess() ? "成功" : "失败");
        observationEngine.observe(result, context);
    }
    
    /**
     * 反思阶段：评估结果，决定下一步
     */
    private ReflectionEngine.ReflectionResult reflect(ActionResult result, AgentContext context, String goal) {
        log.debug("进入反思阶段");
        return reflectionEngine.reflect(result, context, goal);
    }
}

