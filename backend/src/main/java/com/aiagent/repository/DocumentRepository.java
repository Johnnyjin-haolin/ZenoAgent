package com.aiagent.repository;

import com.aiagent.model.Document;

import java.util.List;
import java.util.Optional;

/**
 * 文档数据访问接口
 * 
 * @author aiagent
 */
public interface DocumentRepository {
    
    /**
     * 保存文档
     */
    void save(Document document);
    
    /**
     * 根据ID查找文档
     */
    Optional<Document> findById(String id);
    
    /**
     * 根据知识库ID查找所有文档
     */
    List<Document> findByKnowledgeBaseId(String knowledgeBaseId);
    
    /**
     * 批量保存文档
     */
    void saveAll(List<Document> documents);
    
    /**
     * 删除文档
     */
    void deleteById(String id);
    
    /**
     * 根据知识库ID删除所有文档
     */
    void deleteByKnowledgeBaseId(String knowledgeBaseId);
    
    /**
     * 批量删除文档
     */
    void deleteByIds(List<String> ids);
    
    /**
     * 检查是否存在
     */
    boolean existsById(String id);
}

