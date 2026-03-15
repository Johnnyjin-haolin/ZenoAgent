package com.aiagent.domain.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 推理执行过程记录
 *
 * <p>由 {@link com.aiagent.application.FunctionCallingEngine} 在每次推理执行结束后构建，
 * 并通过 {@code AgentContext.executionProcess} 传递给 {@link com.aiagent.application.AgentServiceImpl}，
 * 最终序列化存入 {@code MessageEntity.metadata.executionProcess}，供历史对话加载时还原。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionProcessRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 迭代轮次列表（每轮包含若干 steps）
     */
    @Builder.Default
    private List<Iteration> iterations = new ArrayList<>();

    /**
     * 总耗时（毫秒）
     */
    private long totalDurationMs;

    // ── 内部类 ──────────────────────────────────────────────────────────────

    /**
     * 单次迭代记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Iteration implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 迭代编号（1-based）*/
        private int iterationNumber;

        /** 步骤列表 */
        @Builder.Default
        private List<Step> steps = new ArrayList<>();

        /** 总耗时（毫秒）*/
        private long durationMs;
    }

    /**
     * 单个步骤记录
     * type: thinking / tool_call / tool_result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 步骤类型
         * <ul>
         *   <li>{@code thinking}   – 中间推理 token（工具调用轮的思考文本）</li>
         *   <li>{@code tool_call}  – 工具调用请求</li>
         *   <li>{@code tool_result} – 工具执行结果</li>
         * </ul>
         */
        private String type;

        /** thinking 内容（type=thinking 时有值）*/
        private String content;

        /** 工具名称（type=tool_call / tool_result 时有值）*/
        private String toolName;

        /** 工具请求参数（type=tool_call 时有值，原始 JSON 字符串）*/
        private String toolParams;

        /** 工具返回内容（type=tool_result 时有值）*/
        private String toolResult;

        /** 工具执行耗时（type=tool_result 时有值，毫秒）*/
        private long toolDurationMs;

        /** 是否出错（type=tool_result 时有值）*/
        private boolean error;

        /** 错误信息（type=tool_result 且 error=true 时有值）*/
        private String errorMessage;
    }
}
