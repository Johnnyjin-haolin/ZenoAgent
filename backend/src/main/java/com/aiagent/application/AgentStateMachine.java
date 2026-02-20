package com.aiagent.application;

import com.aiagent.common.enums.AgentState;
import com.aiagent.domain.model.bo.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Agent状态机
 * 管理Agent执行过程中的状态转换
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class AgentStateMachine {

    private AgentState currentState;
    private AgentContext context;
    private Consumer<AgentState> onStateChange;
    
    /**
     * 初始化状态机
     */
    public void initialize(AgentContext context, Consumer<AgentState> onStateChange) {
        this.currentState = AgentState.INITIALIZING;
        this.context = context;
        this.onStateChange = onStateChange;
        log.info("Agent状态机初始化，初始状态: {}", currentState);
    }
    
    /**
     * 转换到新状态
     */
    public boolean transition(AgentState newState) {
        if (currentState == null) {
            log.warn("状态机未初始化，无法转换状态");
            return false;
        }
        
        if (!currentState.canTransitionTo(newState)) {
            log.warn("无效的状态转换: {} -> {}", currentState, newState);
            return false;
        }
        
        AgentState oldState = currentState;
        currentState = newState;
        
        log.info("Agent状态转换: {} -> {}", oldState, newState);
        
        // 触发状态变更回调
        if (onStateChange != null) {
            try {
                onStateChange.accept(newState);
            } catch (Exception e) {
                log.error("状态变更回调执行失败", e);
            }
        }
        
        return true;
    }
    
    /**
     * 获取当前状态
     */
    public AgentState getCurrentState() {
        return currentState;
    }
    
    /**
     * 检查是否已完成
     */
    public boolean isCompleted() {
        return currentState == AgentState.COMPLETED;
    }
    
    /**
     * 检查是否已失败
     */
    public boolean isFailed() {
        return currentState == AgentState.FAILED;
    }
    
    /**
     * 检查是否为终态
     */
    public boolean isTerminal() {
        return currentState != null && currentState.isTerminal();
    }
    
    /**
     * 重置状态机
     */
    public void reset() {
        this.currentState = AgentState.INITIALIZING;
        log.info("Agent状态机已重置");
    }
}

