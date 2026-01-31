package com.aiagent.application.service.action;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Actions响应DTO
 * 用于接收LLM返回的JSON格式，包含actions数组
 * 
 * @author aiagent
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionsResponseDTO {
    
    /**
     * 动作列表
     */
    private List<ActionInputDTO> actions;
    
    /**
     * 检查actions是否为空
     */
    public boolean hasActions() {
        return actions != null && !actions.isEmpty();
    }
}

