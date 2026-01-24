package com.aiagent.application.service.action;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 直接返回响应参数
 * 用于简单场景，直接返回预设的回复内容，无需调用LLM
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectResponseParams {
    
    /**
     * 要返回的回复内容（必需）
     */
    private String content;
    
    /**
     * 系统提示（可选，用于指导LLM如何格式化回复）
     * 如果提供，会在发送给前端前，通过LLM格式化（可选功能）
     */
    private String systemPrompt;
    
    /**
     * 是否使用流式输出（默认true）
     * true: 流式发送内容，模拟打字效果
     * false: 一次性发送完整内容
     */
    @Builder.Default
    private boolean streaming = true;
}

