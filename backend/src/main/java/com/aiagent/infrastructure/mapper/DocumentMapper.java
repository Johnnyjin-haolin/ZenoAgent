package com.aiagent.infrastructure.mapper;

import com.aiagent.domain.entity.DocumentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档Mapper接口
 * 
 * @author aiagent
 */
@Mapper
public interface DocumentMapper {
    
    /**
     * 插入文档
     */
    void insert(DocumentEntity document);
    
    /**
     * 根据ID查询文档
     */
    DocumentEntity selectById(@Param("id") String id);
    
    /**
     * 根据知识库ID查询所有文档
     */
    List<DocumentEntity> selectByKnowledgeBaseId(@Param("knowledgeBaseId") String knowledgeBaseId);
    
    /**
     * 分页查询文档列表（支持搜索、筛选、排序）
     */
    List<DocumentEntity> selectPage(
        @Param("knowledgeBaseId") String knowledgeBaseId,
        @Param("keyword") String keyword,
        @Param("status") String status,
        @Param("type") String type,
        @Param("orderBy") String orderBy,
        @Param("orderDirection") String orderDirection,
        @Param("offset") int offset,
        @Param("limit") int limit
    );
    
    /**
     * 统计文档数量（支持搜索、筛选）
     */
    int countByConditions(
        @Param("knowledgeBaseId") String knowledgeBaseId,
        @Param("keyword") String keyword,
        @Param("status") String status,
        @Param("type") String type
    );
    
    /**
     * 批量插入文档
     */
    void insertBatch(@Param("documents") List<DocumentEntity> documents);
    
    /**
     * 更新文档
     */
    void update(DocumentEntity document);
    
    /**
     * 删除文档
     */
    void deleteById(@Param("id") String id);
    
    /**
     * 根据知识库ID删除所有文档
     */
    void deleteByKnowledgeBaseId(@Param("knowledgeBaseId") String knowledgeBaseId);
    
    /**
     * 批量删除文档
     */
    void deleteByIds(@Param("ids") List<String> ids);
    
    /**
     * 检查是否存在
     */
    boolean existsById(@Param("id") String id);
}

