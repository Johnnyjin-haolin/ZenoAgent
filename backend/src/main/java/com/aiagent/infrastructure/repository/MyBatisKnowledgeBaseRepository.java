package com.aiagent.infrastructure.repository;

import com.aiagent.domain.entity.KnowledgeBaseEntity;
import com.aiagent.infrastructure.mapper.KnowledgeBaseMapper;
import com.aiagent.domain.model.KnowledgeBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MyBatis实现的知识库数据访问
 * 
 * @author aiagent
 */
@Slf4j
@Repository
public class MyBatisKnowledgeBaseRepository implements KnowledgeBaseRepository {
    
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;
    
    @Override
    public void save(KnowledgeBase knowledgeBase) {
        KnowledgeBaseEntity entity = convertToEntity(knowledgeBase);
        if (knowledgeBaseMapper.existsById(entity.getId())) {
            knowledgeBaseMapper.update(entity);
            log.debug("Updated knowledge base: {}", entity.getId());
        } else {
            knowledgeBaseMapper.insert(entity);
            log.debug("Inserted knowledge base: {}", entity.getId());
        }
    }
    
    @Override
    public Optional<KnowledgeBase> findById(String id) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(id);
        return entity != null ? Optional.of(convertToModel(entity)) : Optional.empty();
    }
    
    @Override
    public List<KnowledgeBase> findAll() {
        List<KnowledgeBaseEntity> entities = knowledgeBaseMapper.selectAll();
        List<KnowledgeBase> result = new ArrayList<>();
        for (KnowledgeBaseEntity entity : entities) {
            result.add(convertToModel(entity));
        }
        return result;
    }
    
    @Override
    public void deleteById(String id) {
        knowledgeBaseMapper.deleteById(id);
        log.debug("Deleted knowledge base: {}", id);
    }
    
    @Override
    public boolean existsById(String id) {
        return knowledgeBaseMapper.existsById(id);
    }
    
    @Override
    public Map<String, KnowledgeBase> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        
        List<KnowledgeBaseEntity> entities = knowledgeBaseMapper.selectByIds(ids);
        Map<String, KnowledgeBase> result = new HashMap<>();
        
        for (KnowledgeBaseEntity entity : entities) {
            KnowledgeBase model = convertToModel(entity);
            result.put(model.getId(), model);
        }
        
        return result;
    }
    
    /**
     * 将 Model 转换为 Entity
     */
    private KnowledgeBaseEntity convertToEntity(KnowledgeBase model) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(model.getId());
        entity.setName(model.getName());
        entity.setDescription(model.getDescription());
        entity.setEmbeddingModelId(model.getEmbeddingModelId());
        entity.setCreateTime(model.getCreateTime());
        entity.setUpdateTime(model.getUpdateTime());
        return entity;
    }
    
    /**
     * 将 Entity 转换为 Model
     */
    private KnowledgeBase convertToModel(KnowledgeBaseEntity entity) {
        return KnowledgeBase.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .embeddingModelId(entity.getEmbeddingModelId())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}

