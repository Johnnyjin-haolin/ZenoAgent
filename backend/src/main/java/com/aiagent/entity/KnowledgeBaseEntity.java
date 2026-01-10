package com.aiagent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库实体类
 * 
 * @author aiagent
 */
@Data
public class KnowledgeBaseEntity {
    
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
     * 向量模型ID
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

