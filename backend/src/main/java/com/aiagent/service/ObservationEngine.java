package com.aiagent.service;

import com.aiagent.vo.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 观察引擎
 * 负责观察动作执行结果，更新上下文
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class ObservationEngine {
    
    @Autowired
    private MemorySystem memorySystem;
    
    /**
     * 观察动作结果，更新上下文
     */
    public void observe(ActionResult result, AgentContext context) {
        log.debug("观察动作结果: {} - {}", result.getActionType(), 
            result.isSuccess() ? "成功" : "失败");
        
        // 更新上下文变量（使用具体属性）
        context.setLastActionResult(result);
        context.setLastActionSuccess(result.isSuccess());
        
        if (result.isSuccess()) {
            // 成功：保存结果到上下文
            context.setLastActionData(result.getData());
            // 清除错误信息
            context.setLastActionError(null);
            context.setLastActionErrorType(null);
            log.debug("动作执行成功，结果已保存到上下文");
        } else {
            // 失败：记录错误信息
            context.setLastActionError(result.getError());
            context.setLastActionErrorType(result.getErrorType());
            // 清除数据
            context.setLastActionData(null);
            log.debug("动作执行失败，错误信息已记录: {}", result.getError());
        }
        
        // 更新迭代次数
        if (context.getIterations() == null) {
            context.setIterations(0);
        }
        context.setIterations(context.getIterations() + 1);
        
        // 保存上下文
        memorySystem.saveContext(context);
    }
}

