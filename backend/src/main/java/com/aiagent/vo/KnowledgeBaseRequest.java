package com.aiagent.vo;

import lombok.Data;

/**
 * 知识库请求参数
 * 
 * @author aiagent
 */
@Data
public class KnowledgeBaseRequest {
    
    /**
     * 名称
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
}

