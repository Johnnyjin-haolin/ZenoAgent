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

}

