package com.aiagent.infrastructure.repository;

import com.aiagent.domain.model.KnowledgeBase;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识库数据访问接口
 * 
 * @author aiagent
 */
public interface KnowledgeBaseRepository {
    
    /**
     * 保存知识库
     */
    void save(KnowledgeBase knowledgeBase);
    
    /**
     * 根据ID查找知识库
     */
    Optional<KnowledgeBase> findById(String id);
    
    /**
     * 查找所有知识库
     */
    List<KnowledgeBase> findAll();
    
    /**
     * 删除知识库
     */
    void deleteById(String id);
    
    /**
     * 检查是否存在
     */
    boolean existsById(String id);
    
    /**
     * 批量根据ID查找知识库
     * 
     * @param ids 知识库ID列表
     * @return 知识库映射（knowledgeId -> KnowledgeBase）
     */
    Map<String, KnowledgeBase> findByIds(List<String> ids);
}

