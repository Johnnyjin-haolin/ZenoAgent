package com.aiagent.model;

/**
 * Agent 执行状态枚举
 * 用于状态机管理Agent的执行流程
 * 
 * @author aiagent
 */
public enum AgentState {
    
    /**
     * 初始化：准备开始执行
     */
    INITIALIZING("初始化", "准备开始执行任务"),
    
    /**
     * 思考：分析任务，理解意图
     */
    THINKING("思考中", "正在分析任务和用户意图"),
    
    /**
     * 规划：制定执行计划
     */
    PLANNING("规划中", "正在制定执行计划"),
    
    /**
     * 执行：执行具体动作
     */
    EXECUTING("执行中", "正在执行动作"),
    
    /**
     * 观察：观察执行结果
     */
    OBSERVING("观察中", "正在观察执行结果"),
    
    /**
     * 反思：评估结果，决定下一步
     */
    REFLECTING("反思中", "正在评估结果并决定下一步"),
    
    /**
     * 完成：任务成功完成
     */
    COMPLETED("已完成", "任务已成功完成"),
    
    /**
     * 失败：任务执行失败
     */
    FAILED("失败", "任务执行失败"),
    
    /**
     * 暂停：任务被暂停
     */
    PAUSED("已暂停", "任务已暂停");
    
    private final String name;
    private final String description;
    
    AgentState(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 检查状态转换是否有效
     */
    public boolean canTransitionTo(AgentState targetState) {
        switch (this) {
            case INITIALIZING:
                return targetState == THINKING || targetState == FAILED;
            case THINKING:
                return targetState == PLANNING || targetState == EXECUTING || 
                       targetState == COMPLETED || targetState == FAILED || targetState == PAUSED;
            case PLANNING:
                return targetState == EXECUTING || targetState == THINKING || 
                       targetState == FAILED || targetState == PAUSED;
            case EXECUTING:
                return targetState == OBSERVING || targetState == FAILED || targetState == PAUSED;
            case OBSERVING:
                return targetState == REFLECTING || targetState == COMPLETED || 
                       targetState == EXECUTING || targetState == FAILED;
            case REFLECTING:
                return targetState == THINKING || targetState == EXECUTING || 
                       targetState == COMPLETED || targetState == FAILED || targetState == PAUSED;
            case COMPLETED:
            case FAILED:
                return false; // 终态，不能转换
            case PAUSED:
                return targetState == THINKING || targetState == EXECUTING || 
                       targetState == COMPLETED || targetState == FAILED;
            default:
                return false;
        }
    }
    
    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
    
    /**
     * 是否为可执行状态
     */
    public boolean isExecutable() {
        return this == EXECUTING || this == OBSERVING;
    }
}

