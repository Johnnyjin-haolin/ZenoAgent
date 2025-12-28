package com.aiagent.repository;

import com.aiagent.model.KnowledgeBase;

import java.util.List;
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
}

