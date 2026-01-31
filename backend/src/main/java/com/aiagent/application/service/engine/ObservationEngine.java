package com.aiagent.application.service.engine;

import com.aiagent.application.service.action.ActionResult;
import com.aiagent.application.service.memory.MemorySystem;
import com.aiagent.application.model.AgentContext;
import com.aiagent.domain.enums.ActionType;
import com.aiagent.domain.enums.AgentState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 观察引擎
 * 负责观察动作执行结果，更新上下文
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class ObservationEngine {
    
    @Autowired
    private MemorySystem memorySystem;
    
    /**
     * 观察动作结果，更新上下文，判断是否结束
     * 
     * @param results 动作执行结果列表
     * @param context Agent上下文
     * @param currentIteration 当前迭代次数
     * @param maxIterations 最大迭代次数
     * @param totalStartNs 总执行开始时间（纳秒）
     * @return 观察结果，包含是否结束和最终执行结果
     */
    public ObservationResult observe(
            List<ActionResult> results, 
            AgentContext context,
            int currentIteration,
            int maxIterations,
            long totalStartNs
    ) {
        log.debug("观察动作结果，数量: {}, 当前迭代: {}/{}", 
            results.size(), currentIteration, maxIterations);
        
        // 1. 检查是否达到最大迭代次数
        if (currentIteration >= maxIterations) {
            log.warn("达到最大迭代次数，结束循环");
            long totalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - totalStartNs);
            List<ChatMessage> allMessages = context.getMessages();
            
            ReActExecutionResult result = ReActExecutionResult.builder()
                .success(false)
                .error("达到最大迭代次数")
                .errorType("MAX_ITERATIONS")
                .messages(allMessages != null ? allMessages : new ArrayList<>())
                .iterations(currentIteration)
                .totalDurationMs(totalDurationMs)
                .finalState(AgentState.FAILED)
                .metadata(new HashMap<>())
                .build();
                
            return ObservationResult.terminate(
                ObservationResult.TerminationReason.MAX_ITERATIONS, 
                result
            );
        }
        
        // 2. 检查结果是否为空
        if (results == null || results.isEmpty()) {
            log.warn("没有动作结果需要观察");
            long totalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - totalStartNs);
            List<ChatMessage> allMessages = context.getMessages();
            
            ReActExecutionResult result = ReActExecutionResult.failure(
                "未产生有效结果",
                "NO_RESULT",
                allMessages,
                currentIteration,
                totalDurationMs,
                AgentState.FAILED
            );
            
            return ObservationResult.terminate(
                ObservationResult.TerminationReason.NO_ACTIONS,
                result
            );
        }

        // 3. 统计成功和失败数量
        long successCount = results.stream().filter(ActionResult::isSuccess).count();
        long failureCount = results.size() - successCount;
        log.debug("动作执行统计: 总数={}, 成功={}, 失败={}", results.size(), successCount, failureCount);

        // 4.收集AI回复消息（DIRECT_RESPONSE 或 LLM_GENERATE 成功时）
        collectAssistantMessages(results, context);
        
        // 5. 更新迭代次数
        context.setIterations(currentIteration);
        
        // 6. 检查是否包含 DIRECT_RESPONSE（任务完成）
        ActionResult completeAction = results.stream()
            .filter(a -> a.getAction() != null && a.getAction().getType() == ActionType.DIRECT_RESPONSE)
            .findFirst()
            .orElse(null);
            
        if (completeAction != null) {
            log.info("检测到 DIRECT_RESPONSE，任务完成");
            long totalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - totalStartNs);
            List<ChatMessage> allMessages = context.getMessages();
            
            ReActExecutionResult result = ReActExecutionResult.success(
                allMessages,
                currentIteration,
                totalDurationMs,
                AgentState.COMPLETED
            );
            
            // 保存上下文
            memorySystem.saveContext(context);
            
            return ObservationResult.terminate(
                ObservationResult.TerminationReason.COMPLETED,
                result
            );
        }
        
        // 7. 保存上下文并继续循环
        // todo: 可选功能，如果执行长度超过，需要精简历史对话
        memorySystem.saveContext(context);
        
        return ObservationResult.continueLoop();
    }

    /**
     * 收集AI回复消息
     * 当 DIRECT_RESPONSE 或 LLM_GENERATE 动作成功时，将回复内容添加到上下文中
     */
    private void collectAssistantMessages(List<ActionResult> results, AgentContext context) {
        if (results == null || results.isEmpty()) {
            return;
        }

        for (ActionResult result : results) {
            // 只处理成功的结果
            if (!result.isSuccess()||result.getAction()==null) {
                continue;
            }

            // 处理 DIRECT_RESPONSE 或 LLM_GENERATE 类型的成功结果
            if (result.getAction().getType() == ActionType.DIRECT_RESPONSE ||
                    result.getAction().getType() == ActionType.LLM_GENERATE) {

                String content = result.getRes();
                if (content != null) {
                    if (!content.trim().isEmpty()) {
                        AiMessage aiMessage = new AiMessage(content);
                        context.addMessage(aiMessage);
                        log.debug("收集AI回复消息，类型: {}, 长度: {}",
                                result.getAction().getType(), content.length());
                    }
                }
            }
        }
    }
}

