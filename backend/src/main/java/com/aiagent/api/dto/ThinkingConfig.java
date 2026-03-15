package com.aiagent.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话历史加载配置（过渡兼容 DTO）
 * <p>
 * 原 PromptReActEngine 时代的 ThinkingConfig 已精简：
 * <ul>
 *   <li>{@code conversationHistoryRounds}    - 已废弃，PromptReActEngine 专属</li>
 *   <li>{@code maxMessageLength}             - 已废弃，PromptReActEngine 专属</li>
 *   <li>{@code actionExecutionHistoryCount}  - 已废弃，PromptReActEngine 专属</li>
 * </ul>
 * 目前只保留 {@code historyMessageLoadLimit}，作为前端临时覆盖的入口；
 * AgentDefinition 级别的默认值请通过 {@link AgentDefinitionRequest.ConversationConfigRequest} 配置。
 *
 * @deprecated 此 DTO 仅作过渡兼容使用，后续将在前端移除对 thinkingConfig 字段的引用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated
public class ThinkingConfig {

    /**
     * 历史消息加载数量（默认 20 条，从数据库加载最近 N 条消息到上下文）
     * 可作为前端临时覆盖；AgentDefinition.conversationConfig.historyMessageLoadLimit 为默认值
     */
    private Integer historyMessageLoadLimit;

    @JsonIgnore
    public int getHistoryMessageLoadLimitOrDefault() {
        return historyMessageLoadLimit != null ? historyMessageLoadLimit : 20;
    }
}
