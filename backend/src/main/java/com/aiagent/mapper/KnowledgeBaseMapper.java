package com.aiagent.mapper;

import com.aiagent.entity.KnowledgeBaseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库Mapper接口
 * 
 * @author aiagent
 */
@Mapper
public interface KnowledgeBaseMapper {
    
    /**
     * 插入知识库
     */
    void insert(KnowledgeBaseEntity knowledgeBase);
    
    /**
     * 根据ID查询知识库
     */
    KnowledgeBaseEntity selectById(@Param("id") String id);
    
    /**
     * 查询所有知识库
     */
    List<KnowledgeBaseEntity> selectAll();
    
    /**
     * 更新知识库
     */
    void update(KnowledgeBaseEntity knowledgeBase);
    
    /**
     * 删除知识库
     */
    void deleteById(@Param("id") String id);
    
    /**
     * 检查是否存在
     */
    boolean existsById(@Param("id") String id);
}

