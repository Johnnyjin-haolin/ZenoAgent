package com.aiagent.application.service.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 观察结果
 * 用于表示观察阶段的判断结果，包含是否应该结束循环以及最终执行结果
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservationResult {
    
    /**
     * 是否应该结束循环
     */
    private boolean shouldTerminate;
    
    /**
     * 终止原因（如果应该结束）
     */
    private TerminationReason terminationReason;
    
    /**
     * 最终执行结果（如果应该结束）
     * 为 null 表示继续循环
     */
    private ReActExecutionResult executionResult;
    
    /**
     * 终止原因枚举
     */
    public enum TerminationReason {
        /**
         * 任务完成（检测到 DIRECT_RESPONSE）
         */
        COMPLETED,
        
        /**
         * 达到最大迭代次数
         */
        MAX_ITERATIONS,
        
        /**
         * 没有产生动作或结果
         */
        NO_ACTIONS,
        
        /**
         * 发生异常
         */
        EXCEPTION,
        
        /**
         * 用户停止
         */
        USER_STOPPED
    }
    
    /**
     * 创建"继续循环"的观察结果
     */
    public static ObservationResult continueLoop() {
        return ObservationResult.builder()
            .shouldTerminate(false)
            .build();
    }
    
    /**
     * 创建"终止循环"的观察结果
     */
    public static ObservationResult terminate(TerminationReason reason, ReActExecutionResult result) {
        return ObservationResult.builder()
            .shouldTerminate(true)
            .terminationReason(reason)
            .executionResult(result)
            .build();
    }
}

