package com.aiagent.domain.tinking;

import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.action.ActionResult;
import com.aiagent.domain.action.AgentAction;

import java.util.List;

/**
 * 思考引擎接口
 * 负责分析当前情况，决定下一步Action
 * 
 * @author aiagent
 */
public interface ThinkingEngine {
    
    /**
     * 思考：分析目标、上下文和历史结果，决定下一步Action
     * 
     * @param goal 目标（用户请求）
     * @param context Agent上下文
     * @param lastResults 上一步执行结果
     * @return 动作列表
     */
    List<AgentAction> think(String goal, AgentContext context, List<ActionResult> lastResults);
}
