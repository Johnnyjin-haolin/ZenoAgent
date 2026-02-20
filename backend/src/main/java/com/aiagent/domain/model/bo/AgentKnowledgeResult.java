package com.aiagent.domain.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 知识检索结果
 * 类型安全的RAG检索结果封装
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentKnowledgeResult {
    
    /**
     * 汇总的知识内容
     */
    private String summary;
    
    /**
     * 文档列表（已排序）
     */
    @Builder.Default
    private List<AgentKnowledgeDocument> documents = new ArrayList<>();
    
    /**
     * 总文档数
     */
    private Integer totalCount;
    
    /**
     * 查询文本
     */
    private String query;
    
    /**
     * 平均相似度分数
     */
    private Double avgScore;
    
    /**
     * 最高相似度分数
     */
    private Double maxScore;
    
    /**
     * 最低相似度分数
     */
    private Double minScore;
    
    /**
     * 判断结果是否为空
     */
    public boolean isEmpty() {
        return documents == null || documents.isEmpty();
    }
    
    /**
     * 判断结果是否不为空
     */
    public boolean isNotEmpty() {
        return !isEmpty();
    }
    
    /**
     * 获取最高分文档
     */
    public AgentKnowledgeDocument getTopDocument() {
        if (isEmpty()) {
            return null;
        }
        return documents.stream()
            .max(Comparator.comparing(doc -> doc.getScore() != null ? doc.getScore() : 0.0))
            .orElse(null);
    }
    
    /**
     * 按分数过滤文档
     */
    public List<AgentKnowledgeDocument> filterByScore(double minScore) {
        if (isEmpty()) {
            return Collections.emptyList();
        }
        return documents.stream()
            .filter(doc -> doc.getScore() != null && doc.getScore() >= minScore)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取高质量文档（分数 >= 0.8）
     */
    public List<AgentKnowledgeDocument> getHighQualityDocuments() {
        return filterByScore(0.8);
    }
    
    /**
     * 获取中等质量文档（0.5 <= 分数 < 0.8）
     */
    public List<AgentKnowledgeDocument> getMediumQualityDocuments() {
        if (isEmpty()) {
            return Collections.emptyList();
        }
        return documents.stream()
            .filter(doc -> doc.getScore() != null 
                        && doc.getScore() >= 0.5 
                        && doc.getScore() < 0.8)
            .collect(Collectors.toList());
    }
}


