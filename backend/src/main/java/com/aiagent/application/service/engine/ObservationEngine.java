package com.aiagent.application.service.engine;

import com.aiagent.api.dto.AgentEventData;
import com.aiagent.application.service.action.ActionResult;
import com.aiagent.application.service.memory.MemorySystem;
import com.aiagent.application.model.AgentContext;
import com.aiagent.domain.enums.ActionType;
import com.aiagent.domain.enums.AgentState;
import com.aiagent.shared.constant.AgentConstants;
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
    
    @Autowired
    private com.aiagent.application.service.agent.StopRequestManager stopRequestManager;
    
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
        log.info("开始观察阶段，当前迭代: {}/{}", currentIteration, maxIterations);
        
        // 【最优先】检查 Redis 中的停止标志（支持多实例）
        String requestId = context.getRequestId();
        if (requestId != null && stopRequestManager.isStopRequested(requestId)) {
            log.info("检测到停止请求（Redis），终止任务: requestId={}", requestId);
            
            // 清除停止标志
            stopRequestManager.clearStopFlag(requestId);
            
            // 发送停止事件
            sendProgressEvent(context, AgentConstants.EVENT_AGENT_COMPLETE, "用户已停止生成");
            
            // 构建停止结果
            long totalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - totalStartNs);
            List<ChatMessage> allMessages = context.getMessages();
            
            ReActExecutionResult executionResult = ReActExecutionResult.builder()
                .success(true) // 停止也算成功
                .messages(allMessages != null ? allMessages : new ArrayList<>())
                .iterations(currentIteration)
                .totalDurationMs(totalDurationMs)
                .finalState(AgentState.COMPLETED)
                .metadata(new HashMap<>())
                .build();
            
            return ObservationResult.builder()
                .shouldTerminate(true)
                .terminationReason(ObservationResult.TerminationReason.USER_STOPPED)
                .executionResult(executionResult)
                .build();
        }
        
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
     * 收集AI回复消息并立即持久化
     * 当 DIRECT_RESPONSE 或 LLM_GENERATE 动作成功时，将回复内容添加到上下文并保存到数据库
     */
    private void collectAssistantMessages(List<ActionResult> results, AgentContext context) {
        if (results == null || results.isEmpty()) {
            return;
        }

        for (ActionResult result : results) {
            // 只处理成功的结果
            if (!result.isSuccess() || result.getAction() == null) {
                continue;
            }

            // 处理 DIRECT_RESPONSE 或 LLM_GENERATE 类型的成功结果
            if (result.getAction().getType() == ActionType.DIRECT_RESPONSE ||
                    result.getAction().getType() == ActionType.LLM_GENERATE) {

                String content = result.getRes();
                if (content != null && !content.trim().isEmpty()) {
                    AiMessage aiMessage = new AiMessage(content);
                    
                    // 1. 添加到上下文
                    context.addMessage(aiMessage);
                    
                    // 2. 立即持久化到数据库
                    persistAiMessage(aiMessage, result, context);
                    
                    log.debug("收集并持久化AI回复消息，类型: {}, 长度: {}",
                            result.getAction().getType(), content.length());
                }
            }
        }
    }
    
    /**
     * 持久化 AI 消息到数据库
     * 
     * @param aiMessage AI消息
     * @param result 动作执行结果
     * @param context Agent上下文
     */
    private void persistAiMessage(AiMessage aiMessage, ActionResult result, AgentContext context) {
        try {
            // 构造元数据
            HashMap<String, Object> metadata = buildMetadata(result);
            
            // 保存到 Redis + MySQL
            memorySystem.saveShortTermMemory(
                context.getConversationId(),
                aiMessage,
                context.getModelId(),
                null,
                (int) result.getDuration(),
                metadata
            );
            
            log.debug("AI消息持久化成功，会话: {}, 内容长度: {}", 
                context.getConversationId(), aiMessage.text().length());
                
        } catch (Exception e) {
            log.error("持久化AI消息失败，会话: {}", context.getConversationId(), e);
            // 失败不影响主流程
        }
    }
    
    /**
     * 从 ActionResult 构造元数据
     * 
     * @param result 动作执行结果
     * @return 元数据Map
     */
    private HashMap<String, Object> buildMetadata(ActionResult result) {
        HashMap<String, Object> metadata = new HashMap<>();
        
        if (result.getAction() != null) {
            metadata.put("actionType", result.getAction().getType().name());
            metadata.put("actionName", result.getAction().getName());
            
            // 添加推理信息
            if (result.getAction().getReasoning() != null) {
                metadata.put("reasoning", result.getAction().getReasoning());
            }
        }
        
        // 添加执行耗时
        if (result.getDuration() > 0) {
            metadata.put("duration", result.getDuration());
        }
        
        return metadata;
    }
    
    /**
     * 发送进度事件到前端
     */
    private void sendProgressEvent(AgentContext context, String event, String message) {
        if (context != null && context.getEventPublisher() != null) {
            context.getEventPublisher().accept(
                AgentEventData.builder()
                    .event(event)
                    .message(message)
                    .conversationId(context.getConversationId())
                    .build()
            );
        }
    }
}

