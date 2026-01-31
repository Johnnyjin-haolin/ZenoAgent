package com.aiagent.application.service.action;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Agent 动作执行结果
 * 包含 AgentAction 和执行结果
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // ========== 基础字段 ==========
    
    /**
     * 执行的动作对象（包含 type、name、params、reasoning 等所有元信息）
     */
    private AgentAction action;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 输出结果（String 格式）
     * - 记录执行动作的输出结果
     * - 已格式化为可读的字符串
     * - 可以是简短摘要或完整内容，根据动作类型决定
     */
    private String res;
    
    /**
     * 执行耗时（毫秒）
     */
    private long duration;
    
    // ========== 错误信息 ==========
    
    /**
     * 错误信息
     */
    private String error;
    
    // ========== 构造方法 ==========
    
    /**
     * 创建成功结果
     */
    public static ActionResult success(AgentAction action, String res) {
        return ActionResult.builder()
            .action(action)
            .success(true)
            .res(res)
            .build();
    }
    
    /**
     * 创建失败结果
     */
    public static ActionResult failure(AgentAction action, String error) {
        return ActionResult.builder()
            .action(action)
            .success(false)
            .error(error)
            .build();
    }

    // ========== 序列化方法 ==========
    
    /**
     * 序列化为 AI 可读的字符串
     * 根据不同的 ActionType 智能格式化
     * 用于 ThinkingEngine 构建提示词
     */
    @Override
    public String toString() {
        if (action == null) {
            return "无效的动作结果";
        }
        
        StringBuilder sb = new StringBuilder();
        
        // 1. 基础信息
        sb.append("动作：").append(action.getName())
          .append("（").append(action.getType()).append("）\n");
        
        // 2. 执行状态
        if (success) {
            sb.append("状态：✓ 成功");
            if (duration > 0) {
                sb.append("（").append(duration).append("ms）");
            }
            sb.append("\n");
        } else {
            sb.append("状态：✗ 失败\n");
        }
        
        // 3. 根据 ActionType 展示入参（从 action 中获取）
        String inputInfo = formatActionInput(action);
        if (inputInfo != null && !inputInfo.isEmpty()) {
            sb.append("入参：").append(inputInfo).append("\n");
        }
        
        // 4. 展示出参或错误
        if (success && res != null && !res.isEmpty()) {
            sb.append("出参：").append(res).append("\n");
        } else if (!success && error != null && !error.isEmpty()) {
            sb.append("错误：").append(error).append("\n");
        }
        
        // 5. 可选：展示推理过程
        if (action.getReasoning() != null && !action.getReasoning().isEmpty()) {
            sb.append("推理：").append(truncate(action.getReasoning(), 100)).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 根据 ActionType 格式化输入参数
     * 从 AgentAction 中提取参数信息
     */
    private String formatActionInput(AgentAction action) {
        if (action == null) {
            return "";
        }

        return switch (action.getType()) {
            case TOOL_CALL -> formatToolCallInput(action);
            case RAG_RETRIEVE -> formatRAGInput(action);
            case LLM_GENERATE -> formatLLMInput(action);
            case DIRECT_RESPONSE -> formatDirectResponseInput(action);
        };
    }
    
    /**
     * 格式化工具调用的入参
     */
    private String formatToolCallInput(AgentAction action) {
        ToolCallParams params = action.getToolCallParams();
        if (params == null) {
            return "toolName=" + action.getName();
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("toolName=").append(action.getName());
        
        if (params.getToolParams() != null && !params.getToolParams().isEmpty()) {
            sb.append(", params={");
            params.getToolParams().forEach((key, value) -> 
                sb.append(key).append("=").append(value).append(", ")
            );
            // 移除最后的 ", "
            int length = sb.length();
            if (length > 2 && sb.charAt(length - 2) == ',' && sb.charAt(length - 1) == ' ') {
                sb.setLength(length - 2);
            }
            sb.append("}");
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化 RAG 检索的入参
     */
    private String formatRAGInput(AgentAction action) {
        RAGRetrieveParams params = action.getRagRetrieveParams();
        if (params == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("query=").append(params.getQuery());
        
        if (params.getKnowledgeIds() != null && !params.getKnowledgeIds().isEmpty()) {
            sb.append(", knowledgeIds=[");
            sb.append(String.join(", ", params.getKnowledgeIds()));
            sb.append("]");
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化 LLM 生成的入参
     */
    private String formatLLMInput(AgentAction action) {
        LLMGenerateParams params = action.getLlmGenerateParams();
        if (params == null) {
            return "";
        }
        
        return "prompt=" + truncate(params.getPrompt(), 100);
    }
    
    /**
     * 格式化直接响应的入参
     */
    private String formatDirectResponseInput(AgentAction action) {
        DirectResponseParams params = action.getDirectResponseParams();
        if (params == null) {
            return "";
        }
        
        return "content=" + truncate(params.getContent(), 50) + ", streaming=true";
    }
    
    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }
}
