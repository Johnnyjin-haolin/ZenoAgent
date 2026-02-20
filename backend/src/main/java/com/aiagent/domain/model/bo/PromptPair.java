package com.aiagent.domain.model.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提示词对，用于分离系统提示词和用户提示词
 * 
 * @author aiagent
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromptPair {
    
    /**
     * 系统提示词
     * 包含静态的规则、约束、输出格式等
     */
    private String systemPrompt;
    
    /**
     * 用户提示词
     * 包含动态的目标、历史、工具列表等上下文信息
     */
    private String userPrompt;
}

