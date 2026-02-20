package com.aiagent.domain.action;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具调用参数
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallParams {
    
    /**
     * 工具参数（传递给工具的实际参数）
     */
    private String toolParams;
    
    /**
     * 工具名称（可选，如果AgentAction中已有name，可以省略）
     */
    private String toolName;
}

