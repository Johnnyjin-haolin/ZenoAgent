package com.aiagent.application.service.engine;

import com.aiagent.application.service.action.ActionResult;
import com.aiagent.application.service.action.AgentAction;
import com.aiagent.application.service.memory.MemorySystem;
import com.aiagent.application.model.AgentContext;
import com.aiagent.domain.enums.ActionType;
import com.aiagent.domain.enums.AgentState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

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
     * 观察动作结果，更新上下文，返回是否结束
     */
    public boolean observe(List<ActionResult> results, AgentContext context) {
        log.debug("观察动作结果，数量: {}", results.size());
        
        if (results == null || results.isEmpty()) {
            log.warn("没有动作结果需要观察");
            return true;
        }
        
        // 处理每个结果，更新上下文
        // 使用最后一个结果作为主要上下文信息（用于向后兼容）
        ActionResult lastResult = results.get(results.size() - 1);
        context.setLastActionResult(lastResult);
        context.setLastActionSuccess(lastResult.isSuccess());
        
        if (lastResult.isSuccess()) {
            // 成功：保存结果到上下文
            context.setLastActionData(lastResult.getData());
            // 清除错误信息
            context.setLastActionError(null);
            context.setLastActionErrorType(null);
            log.debug("最后一个动作执行成功，结果已保存到上下文");
        } else {
            // 失败：记录错误信息
            context.setLastActionError(lastResult.getError());
            context.setLastActionErrorType(lastResult.getErrorType());
            // 清除数据
            context.setLastActionData(null);
            log.debug("最后一个动作执行失败，错误信息已记录: {}", lastResult.getError());
        }
        
        // 统计成功和失败数量
        long successCount = results.stream().filter(ActionResult::isSuccess).count();
        long failureCount = results.size() - successCount;
        log.debug("动作执行统计: 总数={}, 成功={}, 失败={}", results.size(), successCount, failureCount);
        
        // 更新迭代次数
        if (context.getIterations() == null) {
            context.setIterations(0);
        }
        context.setIterations(context.getIterations() + 1);

        ActionResult completeAction = results.stream()
                .filter(a -> a.getActionType() == ActionType.DIRECT_RESPONSE)
                .findFirst()
                .orElse(null);
        if (completeAction != null) {
            return true;
        }
        //todo :可选功能，如果执行长度超过，需要精简历史对话
        
        // 保存上下文
        memorySystem.saveContext(context);
        return false;
    }
}

