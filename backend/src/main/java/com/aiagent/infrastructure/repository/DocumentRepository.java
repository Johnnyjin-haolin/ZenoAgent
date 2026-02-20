package com.aiagent.infrastructure.repository;

import com.aiagent.domain.model.bo.Document;

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
     * 分页查询文档列表（支持搜索、筛选、排序）
     * 
     * @param knowledgeBaseId 知识库ID
     * @param keyword 关键词（文档名称搜索）
     * @param status 状态筛选
     * @param type 类型筛选
     * @param orderBy 排序字段
     * @param orderDirection 排序方向（ASC/DESC）
     * @param offset 偏移量
     * @param limit 每页数量
     * @return 文档列表
     */
    List<Document> findPage(
        String knowledgeBaseId,
        String keyword,
        String status,
        String type,
        String orderBy,
        String orderDirection,
        int offset,
        int limit
    );
    
    /**
     * 统计文档数量（支持搜索、筛选）
     */
    int countByConditions(
        String knowledgeBaseId,
        String keyword,
        String status,
        String type
    );
    
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

