package com.aiagent.service;

import com.aiagent.vo.AgentContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 反思引擎
 * 负责评估动作执行结果，决定下一步行动
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class ReflectionEngine {
    
    /**
     * 反思：评估结果，决定下一步
     */
    public ReflectionResult reflect(ActionResult result, AgentContext context, String goal) {
        log.info("开始反思，目标: {}", goal);
        
        ReflectionResult reflection = ReflectionResult.builder()
            .goalAchieved(false)
            .shouldContinue(false)
            .shouldRetry(false)
            .build();
        
        // 分析结果
        if (result.isSuccess()) {
            // 成功：检查是否达成目标
            boolean goalAchieved = checkGoalAchieved(result, goal, context);
            reflection.setGoalAchieved(goalAchieved);
            
            if (goalAchieved) {
                reflection.setSummary("目标已达成: " + result.getData());
                log.info("反思结果：目标已达成");
            } else {
                reflection.setShouldContinue(true);
                reflection.setSummary("动作执行成功，但目标尚未完全达成，继续执行下一步");
                log.info("反思结果：继续执行");
            }
        } else {
            // 失败：分析失败原因，决定是否重试
            FailureAnalysis analysis = analyzeFailure(result, context);
            reflection.setFailureReason(analysis.getReason());
            reflection.setFailureType(analysis.getType());
            
            if (analysis.isRetryable()) {
                reflection.setShouldRetry(true);
                reflection.setRetryReason(analysis.getRetryReason());
                log.info("反思结果：需要重试，原因: {}", analysis.getRetryReason());
            } else {
                reflection.setShouldContinue(false);
                reflection.setSummary("动作执行失败且不可重试: " + result.getError());
                log.warn("反思结果：失败且不可重试");
            }
        }
        
        return reflection;
    }
    
    /**
     * 检查目标是否已达成
     */
    private boolean checkGoalAchieved(ActionResult result, String goal, AgentContext context) {
        // 简单实现：如果动作类型是COMPLETE，则认为目标已达成
        if ("complete".equals(result.getActionType())) {
            return true;
        }
        
        // 可以根据结果内容和目标进行更复杂的判断
        // TODO: 使用LLM判断目标是否达成
        
        return false;
    }
    
    /**
     * 分析失败原因
     */
    private FailureAnalysis analyzeFailure(ActionResult result, AgentContext context) {
        String errorType = result.getErrorType();
        String error = result.getError();
        
        FailureAnalysis analysis = new FailureAnalysis();
        analysis.setReason(error);
        analysis.setType(errorType);
        
        // 判断是否可重试
        if ("TOOL_CALL_ERROR".equals(errorType) || "RAG_RETRIEVE_ERROR".equals(errorType)) {
            // 工具调用或RAG检索错误，可以重试
            analysis.setRetryable(true);
            analysis.setRetryReason("工具调用失败，可能是临时错误，可以重试");
        } else if ("EXCEPTION".equals(errorType)) {
            // 异常，根据错误信息判断
            if (error != null && (error.contains("timeout") || error.contains("网络"))) {
                analysis.setRetryable(true);
                analysis.setRetryReason("网络或超时错误，可以重试");
            } else {
                analysis.setRetryable(false);
            }
        } else {
            analysis.setRetryable(false);
        }
        
        return analysis;
    }
    
    /**
     * 反思结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReflectionResult {
        /**
         * 目标是否已达成
         */
        private boolean goalAchieved;
        
        /**
         * 是否应该继续执行
         */
        private boolean shouldContinue;
        
        /**
         * 是否应该重试
         */
        private boolean shouldRetry;
        
        /**
         * 重试原因
         */
        private String retryReason;
        
        /**
         * 失败原因
         */
        private String failureReason;
        
        /**
         * 失败类型
         */
        private String failureType;
        
        /**
         * 总结
         */
        private String summary;
    }
    
    /**
     * 失败分析
     */
    @Data
    private static class FailureAnalysis {
        private String reason;
        private String type;
        private boolean retryable;
        private String retryReason;
    }
}

