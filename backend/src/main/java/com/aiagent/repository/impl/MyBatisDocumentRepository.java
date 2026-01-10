package com.aiagent.repository.impl;

import com.aiagent.entity.DocumentEntity;
import com.aiagent.mapper.DocumentMapper;
import com.aiagent.model.Document;
import com.aiagent.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis实现的文档数据访问
 * 
 * @author aiagent
 */
@Slf4j
@Repository
public class MyBatisDocumentRepository implements DocumentRepository {
    
    @Autowired
    private DocumentMapper documentMapper;
    
    @Override
    public void save(Document document) {
        DocumentEntity entity = convertToEntity(document);
        if (documentMapper.existsById(entity.getId())) {
            documentMapper.update(entity);
            log.debug("Updated document: {}", entity.getId());
        } else {
            documentMapper.insert(entity);
            log.debug("Inserted document: {}", entity.getId());
        }
    }
    
    @Override
    public Optional<Document> findById(String id) {
        DocumentEntity entity = documentMapper.selectById(id);
        return entity != null ? Optional.of(convertToModel(entity)) : Optional.empty();
    }
    
    @Override
    public List<Document> findByKnowledgeBaseId(String knowledgeBaseId) {
        List<DocumentEntity> entities = documentMapper.selectByKnowledgeBaseId(knowledgeBaseId);
        List<Document> result = new ArrayList<>();
        for (DocumentEntity entity : entities) {
            result.add(convertToModel(entity));
        }
        return result;
    }
    
    @Override
    public void saveAll(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<DocumentEntity> entities = new ArrayList<>();
        for (Document document : documents) {
            entities.add(convertToEntity(document));
        }
        documentMapper.insertBatch(entities);
        log.debug("Batch inserted {} documents", documents.size());
    }
    
    @Override
    public void deleteById(String id) {
        documentMapper.deleteById(id);
        log.debug("Deleted document: {}", id);
    }
    
    @Override
    public void deleteByKnowledgeBaseId(String knowledgeBaseId) {
        documentMapper.deleteByKnowledgeBaseId(knowledgeBaseId);
        log.debug("Deleted all documents for knowledge base: {}", knowledgeBaseId);
    }
    
    @Override
    public void deleteByIds(List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            documentMapper.deleteByIds(ids);
            log.debug("Batch deleted {} documents", ids.size());
        }
    }
    
    @Override
    public boolean existsById(String id) {
        return documentMapper.existsById(id);
    }
    
    /**
     * 将 Model 转换为 Entity
     */
    private DocumentEntity convertToEntity(Document model) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(model.getId());
        entity.setKnowledgeBaseId(model.getKnowledgeBaseId());
        entity.setTitle(model.getTitle());
        entity.setType(model.getType());
        entity.setContent(model.getContent());
        entity.setMetadata(model.getMetadata());
        entity.setStatus(model.getStatus());
        entity.setCreateTime(model.getCreateTime());
        entity.setUpdateTime(model.getUpdateTime());
        return entity;
    }
    
    /**
     * 将 Entity 转换为 Model
     */
    private Document convertToModel(DocumentEntity entity) {
        return Document.builder()
                .id(entity.getId())
                .knowledgeBaseId(entity.getKnowledgeBaseId())
                .title(entity.getTitle())
                .type(entity.getType())
                .content(entity.getContent())
                .metadata(entity.getMetadata())
                .status(entity.getStatus())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}

