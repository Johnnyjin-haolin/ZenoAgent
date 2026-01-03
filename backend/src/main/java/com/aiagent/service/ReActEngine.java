package com.aiagent.service;

import com.aiagent.constant.AgentConstants;
import com.aiagent.enums.AgentState;
import com.aiagent.vo.AgentContext;
import com.aiagent.vo.AgentEventData;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

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
                sendProgressEvent(context, AgentConstants.EVENT_AGENT_PLANNING, "正在规划下一步...");
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
                // 发送执行事件（具体的工具名称会在ActionExecutor中发送）
                ActionResult result = act(action, context);
                lastResult = result;

                log.info("观察开始");
                
                // 3. 观察阶段（Observe）
                stateMachine.transition(AgentState.OBSERVING);
                sendProgressEvent(context, AgentConstants.EVENT_AGENT_OBSERVING, "正在观察执行结果...");
                observe(result, context);

                log.info("反思开始");
                
                // 4. 反思阶段（Reflect）
                stateMachine.transition(AgentState.REFLECTING);
                sendProgressEvent(context, AgentConstants.EVENT_AGENT_REFLECTING, "结果反思中...");
                ReflectionEngine.ReflectionResult reflection = reflect(result, context, goal);
                log.info("反思结束，{}", JSON.toJSONString(reflection));
                // 根据反思结果决定下一步
                if (reflection.isGoalAchieved()) {
                    log.info("反思结果：目标已达成");
                    stateMachine.transition(AgentState.COMPLETED);
                    
                    // 判断是否需要生成友好总结
                    if (reflection.isNeedsSummary()) {
                        log.info("需要生成友好总结，使用流式API生成");
                        
                        // 发送总结进度事件
                        sendProgressEvent(context, AgentConstants.EVENT_AGENT_GENERATING, "正在总结完成情况...");
                        
                        // 使用流式API生成总结
                        ActionResult summaryResult = generateCompletionSummaryWithStreaming(goal, context, result);
                        
                        // 返回总结结果（如果总结失败，则返回原始结果）
                        if (summaryResult.isSuccess()) {
                            return summaryResult;
                        } else {
                            log.warn("生成总结失败，返回原始结果");
                            return ActionResult.success("complete", "complete", result.getData());
                        }
                    } else {
                        log.info("不需要总结，直接返回结果");
                        // 简单对话等场景，直接返回LLM生成的结果
                        return ActionResult.success("complete", "complete", result.getData());
                    }
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
    
    /**
     * 使用流式API生成友好的完成总结
     * 当目标达成时，基于整个执行过程生成一个对用户友好的总结（流式输出）
     */
    private ActionResult generateCompletionSummaryWithStreaming(String goal, AgentContext context, ActionResult lastResult) {
        log.info("使用流式API生成任务完成总结");
        
        try {
            // 构建总结提示词
            String summaryPrompt = buildCompletionSummaryPrompt(goal, context, lastResult);
            
            // 创建LLM_GENERATE动作（会使用流式输出）
            AgentAction summaryAction = AgentAction.llmGenerate(
                com.aiagent.service.action.LLMGenerateParams.builder()
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
    private String buildCompletionSummaryPrompt(String goal, AgentContext context, ActionResult lastResult) {
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
        
        // 添加最终执行结果
        if (lastResult != null && lastResult.getData() != null) {
            String resultData = lastResult.getData().toString();
            // 限制长度
            if (resultData.length() > 2000) {
                resultData = resultData.substring(0, 2000) + "... (结果过长，已截断)";
            }
            prompt.append("最终执行结果:\n").append(resultData).append("\n\n");
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
}

