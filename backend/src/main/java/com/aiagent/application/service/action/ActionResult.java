package com.aiagent.application.service.action;

import com.aiagent.domain.enums.ActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent动作执行结果
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResult {
    
    /**
     * 是否成功
     */
    private boolean success;
    /**
     * 结果数据
     */
    private Object data;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 错误类型
     */
    private String errorType;
    
    /**
     * 执行的动作类型
     */
    private ActionType actionType;
    /**
     * 动作名称
     */
    private String actionName;
    
    /**
     * 执行耗时（毫秒）
     */
    private long duration;

    /**
     * 元数据,这里记录的是ai需要感知的数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 创建成功结果
     */
    public static ActionResult success(ActionType actionType, String actionName, Object data) {
        return ActionResult.builder()
            .success(true)
            .actionType(actionType)
            .actionName(actionName)
            .data(data)
            .build();
    }
    
    /**
     * 创建失败结果
     */
    public static ActionResult failure(ActionType actionType, String actionName, String error, String errorType) {
        return ActionResult.builder()
            .success(false)
            .actionType(actionType)
            .actionName(actionName)
            .error(error)
            .errorType(errorType)
            .build();
    }
    
    /**
     * 判断是否失败
     */
    public boolean isFailure() {
        return !success;
    }
}

