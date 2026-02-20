package com.aiagent.application.service.action;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
@JsonIgnoreProperties(ignoreUnknown = true)
public class DirectResponseParams {
    
    /**
     * 要返回的回复内容（必需）
     */
    private String content;

    /**
     * 是否完成任务（可选，默认false）
     */
    private Boolean isComplete;
    public boolean checkComplete() {
        return isComplete != null? isComplete : false;
    }

}

