package com.aiagent.service;

import com.aiagent.enums.TaskType;
import com.aiagent.util.StringUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 任务分类器（重构版）
 * 使用LLM进行智能任务分类，替代简单的关键词匹配
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class TaskClassifier {
    
    @Autowired
    private IntelligentToolSelector toolSelector;
    
    // 保留关键词匹配作为快速路径
    private static final List<String> SIMPLE_CHAT_KEYWORDS = Arrays.asList(
        "你好", "hi", "hello", "谢谢", "再见"
    );
    
    private static final List<String> TOOL_CALL_KEYWORDS = Arrays.asList(
        "打开", "关闭", "控制", "执行", "调用", "设备"
    );
    
    /**
     * 分类任务类型（使用LLM）
     * 
     * @param userInput 用户输入
     * @param context Agent上下文
     * @return 任务类型
     */
    public TaskType classify(String userInput, com.aiagent.vo.AgentContext context) {
        log.info("开始任务分类，用户输入: {}", userInput);
        
        // 快速路径：简单关键词匹配
        TaskType quickMatch = quickMatch(userInput);
        if (quickMatch != null) {
            log.info("快速匹配结果: {}", quickMatch);
            return quickMatch;
        }
        
        // 使用LLM进行智能分类
        try {
            TaskType llmResult = classifyWithLLM(userInput, context);
            log.info("LLM分类结果: {}", llmResult);
            return llmResult;
        } catch (Exception e) {
            log.error("LLM分类失败，使用默认类型", e);
            return TaskType.SIMPLE_CHAT;
        }
    }
    
    /**
     * 使用LLM进行任务分类
     */
    private TaskType classifyWithLLM(String userInput, com.aiagent.vo.AgentContext context) {
        // TODO: 实现完整的LLM分类
        // 当前简化实现：使用默认分类逻辑
        log.warn("LLM分类未完全实现，使用默认逻辑");
        return TaskType.SIMPLE_CHAT;
    }
    
    
    /**
     * 解析分类结果
     */
    private TaskType parseClassificationResult(String result) {
        if (StringUtils.isEmpty(result)) {
            return TaskType.SIMPLE_CHAT;
        }
        
        // 尝试从结果中提取任务类型
        String upperResult = result.trim().toUpperCase();
        
        // 直接匹配
        for (TaskType type : TaskType.values()) {
            if (upperResult.contains(type.name())) {
                return type;
            }
        }
        
        // 尝试解析JSON
        try {
            JSONObject json = JSON.parseObject(result);
            String typeStr = json.getString("taskType");
            if (typeStr != null) {
                return TaskType.valueOf(typeStr);
            }
        } catch (Exception e) {
            log.debug("解析JSON失败，使用文本匹配");
        }
        
        // 默认返回简单对话
        return TaskType.SIMPLE_CHAT;
    }
    
    /**
     * 快速关键词匹配（保留作为快速路径）
     */
    private TaskType quickMatch(String userInput) {
        if (StringUtils.isEmpty(userInput)) {
            return TaskType.SIMPLE_CHAT;
        }
        
        String input = userInput.toLowerCase();
        
        // 简单对话
        if (SIMPLE_CHAT_KEYWORDS.stream().anyMatch(input::contains)) {
            return TaskType.SIMPLE_CHAT;
        }
        
        // 工具调用
        if (TOOL_CALL_KEYWORDS.stream().anyMatch(input::contains)) {
            return TaskType.TOOL_CALL;
        }
        
        return null;
    }
}


