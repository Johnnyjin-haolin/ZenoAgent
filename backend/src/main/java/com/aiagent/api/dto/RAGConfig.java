package com.aiagent.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RAG配置参数
 * 用于控制知识库检索和内容处理
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // ===== 检索参数 =====
    
    /**
     * 最大检索文档数量（默认3，从知识库检索的文档数）
     * 减少可降低Token使用量
     * 注意：不要使用 @Builder.Default，会导致Jackson反序列化问题
     */
    private Integer maxResults;
    
    /**
     * 最小相似度分数（默认0.5，范围0-1）
     * 只返回相似度高于此阈值的文档
     * 注意：不要使用 @Builder.Default，会导致Jackson反序列化问题
     */
    private Double minScore;
    
    // ===== Token控制参数（支持null表示无限制）=====
    
    /**
     * 单个文档内容最大字符数（默认1000，超过截断）
     * null 表示不限制单文档长度
     * 注意：不要使用 @Builder.Default，会导致Jackson反序列化问题
     */
    @JsonProperty("maxDocumentLength")
    private Integer maxDocumentLength;
    
    /**
     * 所有文档总内容最大字符数（默认3000）
     * null 表示不限制总长度
     * 注意：不要使用 @Builder.Default，会导致Jackson反序列化问题
     */
    @JsonProperty("maxTotalContentLength")
    private Integer maxTotalContentLength;
    
    /**
     * 是否在提示词中包含RAG结果（默认true）
     * 关闭后可显著减少Token使用
     * 注意：不要使用 @Builder.Default，会导致Jackson反序列化问题
     */
    private Boolean includeInPrompt;
    
    /**
     * 是否启用智能摘要（默认false，实验性功能）
     * 开启后会对长文档进行智能摘要而非简单截断
     * 注意：不要使用 @Builder.Default，会导致Jackson反序列化问题
     */
    private Boolean enableSmartSummary;
    
    // ===== 辅助方法 =====
    
    @JsonIgnore
    public int getMaxResultsOrDefault() {
        return maxResults != null && maxResults > 0 ? maxResults : 3;
    }
    
    @JsonIgnore
    public double getMinScoreOrDefault() {
        return minScore != null && minScore >= 0 ? minScore : 0.5;
    }
    
    /**
     * 获取单文档最大长度
     * @return null表示无限制，否则返回具体值
     */
    @JsonIgnore
    public Integer getMaxDocumentLength() {
        return maxDocumentLength;
    }
    
    /**
     * 是否限制单文档长度
     */
    @JsonIgnore
    public boolean hasDocumentLengthLimit() {
        return maxDocumentLength != null && maxDocumentLength > 0;
    }
    
    /**
     * 获取总内容最大长度
     * @return null表示无限制，否则返回具体值
     */
    @JsonIgnore
    public Integer getMaxTotalContentLength() {
        return maxTotalContentLength;
    }
    
    /**
     * 是否限制总内容长度
     */
    @JsonIgnore
    public boolean hasTotalContentLengthLimit() {
        return maxTotalContentLength != null && maxTotalContentLength > 0;
    }
    
    @JsonIgnore
    public boolean getIncludeInPromptOrDefault() {
        return includeInPrompt != null ? includeInPrompt : true;
    }
    
    @JsonIgnore
    public boolean getEnableSmartSummaryOrDefault() {
        return enableSmartSummary != null ? enableSmartSummary : false;
    }
}

