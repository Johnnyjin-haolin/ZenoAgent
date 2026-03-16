package com.aiagent.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RAG 检索参数
 * <p>
 * 同时用于：
 * <ul>
 *   <li>{@link com.aiagent.domain.agent.AgentDefinition#ragConfig} —— Agent 定义级别的持久化配置</li>
 *   <li>{@link com.aiagent.domain.model.bo.AgentRuntimeConfig#ragConfig} —— 运行时注入到 AgentContext</li>
 * </ul>
 * 只保留 {@link com.aiagent.domain.rag.RAGEnhancer} 实际使用的参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    // ===== 检索参数 =====

    /**
     * 最大检索文档数量（默认 3，从知识库检索的文档数）
     * 减少可降低 Token 使用量
     */
    private Integer maxResults;

    /**
     * 最小相似度分数（默认 0.5，范围 0-1）
     * 只返回相似度高于此阈值的文档
     */
    private Double minScore;

    // ===== Token 控制参数 =====

    /**
     * 单个文档内容最大字符数（默认 1000，null 表示不限制）
     */
    @JsonProperty("maxDocumentLength")
    private Integer maxDocumentLength;

    /**
     * 所有文档总内容最大字符数（默认 3000，null 表示不限制）
     */
    @JsonProperty("maxTotalContentLength")
    private Integer maxTotalContentLength;

    // ===== 辅助方法 =====

    @JsonIgnore
    public int getMaxResultsOrDefault() {
        return maxResults != null && maxResults > 0 ? maxResults : 3;
    }

    @JsonIgnore
    public double getMinScoreOrDefault() {
        return minScore != null && minScore >= 0 ? minScore : 0.5;
    }

    @JsonIgnore
    public boolean hasDocumentLengthLimit() {
        return maxDocumentLength != null && maxDocumentLength > 0;
    }

    @JsonIgnore
    public boolean hasTotalContentLengthLimit() {
        return maxTotalContentLength != null && maxTotalContentLength > 0;
    }
}
