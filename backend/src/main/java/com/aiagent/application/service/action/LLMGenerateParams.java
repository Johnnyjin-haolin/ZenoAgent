package com.aiagent.application.service.action;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM生成参数
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMGenerateParams {
    
    /**
     * 生成提示词
     */
    private String prompt;
    
    /**
     * 系统提示词（可选）
     */
    private String systemPrompt;
    
    /**
     * 温度参数（可选）
     */
    private Double temperature;
    
    /**
     * 最大token数（可选）
     */
    private Integer maxTokens;
    
    /**
     * 上下文消息列表（可选）
     */
    @Builder.Default
    private List<Object> contextMessages = new ArrayList<>();
}

