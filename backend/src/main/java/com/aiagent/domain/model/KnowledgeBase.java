package com.aiagent.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库实体
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBase {
    
    /**
     * 主键ID
     */
    private String id;
    
    /**
     * 知识库名称
     */
    private String name;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 向量模型ID（用于生成embeddings）
     */
    private String embeddingModelId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

