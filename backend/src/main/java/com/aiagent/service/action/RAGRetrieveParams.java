package com.aiagent.service.action;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG检索参数
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGRetrieveParams {
    
    /**
     * 检索查询文本
     */
    private String query;
    
    /**
     * 知识库ID列表
     */
    @Builder.Default
    private List<String> knowledgeIds = new ArrayList<>();
    
    /**
     * 最大返回结果数（可选）
     */
    private Integer maxResults;
    
    /**
     * 相似度阈值（可选）
     */
    private Double similarityThreshold;
}

