package com.aiagent.repository.impl;

import com.aiagent.model.Document;
import com.aiagent.repository.DocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis实现的文档数据访问
 * 
 * @author aiagent
 */
@Slf4j
@Repository
public class RedisDocumentRepository implements DocumentRepository {
    
    private static final String KEY_PREFIX = "document:";
    private static final String INDEX_KEY = "document:index";
    private static final String KB_INDEX_PREFIX = "document:kb:";
    private static final long TTL_DAYS = 365; // 1年过期时间
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private final ObjectMapper objectMapper;
    
    public RedisDocumentRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public void save(Document document) {
        try {
            String id = document.getId();
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Document ID cannot be null or empty");
            }
            
            // 设置更新时间
            document.setUpdateTime(LocalDateTime.now());
            if (document.getCreateTime() == null) {
                document.setCreateTime(LocalDateTime.now());
            }
            
            String key = KEY_PREFIX + id;
            String json = objectMapper.writeValueAsString(document);
            
            // 保存数据
            redisTemplate.opsForValue().set(key, json, TTL_DAYS, TimeUnit.DAYS);
            
            // 添加到索引
            redisTemplate.opsForSet().add(INDEX_KEY, id);
            
            // 添加到知识库索引
            String kbIndexKey = KB_INDEX_PREFIX + document.getKnowledgeBaseId();
            redisTemplate.opsForSet().add(kbIndexKey, id);
            
            log.debug("Saved document: {}", id);
        } catch (Exception e) {
            log.error("Failed to save document", e);
            throw new RuntimeException("Failed to save document", e);
        }
    }
    
    @Override
    public Optional<Document> findById(String id) {
        try {
            String key = KEY_PREFIX + id;
            String json = redisTemplate.opsForValue().get(key);
            
            if (json == null || json.isEmpty()) {
                return Optional.empty();
            }
            
            Document document = objectMapper.readValue(json, Document.class);
            return Optional.of(document);
        } catch (Exception e) {
            log.error("Failed to find document by id: {}", id, e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<Document> findByKnowledgeBaseId(String knowledgeBaseId) {
        try {
            String kbIndexKey = KB_INDEX_PREFIX + knowledgeBaseId;
            Set<String> ids = redisTemplate.opsForSet().members(kbIndexKey);
            if (ids == null || ids.isEmpty()) {
                return new ArrayList<>();
            }
            
            List<Document> result = new ArrayList<>();
            for (String id : ids) {
                findById(id).ifPresent(result::add);
            }
            
            return result;
        } catch (Exception e) {
            log.error("Failed to find documents by knowledge base id: {}", knowledgeBaseId, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void saveAll(List<Document> documents) {
        for (Document document : documents) {
            save(document);
        }
    }
    
    @Override
    public void deleteById(String id) {
        try {
            Optional<Document> documentOpt = findById(id);
            if (documentOpt.isPresent()) {
                Document document = documentOpt.get();
                
                // 从知识库索引中移除
                String kbIndexKey = KB_INDEX_PREFIX + document.getKnowledgeBaseId();
                redisTemplate.opsForSet().remove(kbIndexKey, id);
            }
            
            // 删除数据
            String key = KEY_PREFIX + id;
            redisTemplate.delete(key);
            
            // 从主索引中移除
            redisTemplate.opsForSet().remove(INDEX_KEY, id);
            
            log.debug("Deleted document: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete document: {}", id, e);
            throw new RuntimeException("Failed to delete document", e);
        }
    }
    
    @Override
    public void deleteByKnowledgeBaseId(String knowledgeBaseId) {
        try {
            List<Document> documents = findByKnowledgeBaseId(knowledgeBaseId);
            List<String> ids = documents.stream()
                    .map(Document::getId)
                    .collect(Collectors.toList());
            
            deleteByIds(ids);
            
            // 删除知识库索引
            String kbIndexKey = KB_INDEX_PREFIX + knowledgeBaseId;
            redisTemplate.delete(kbIndexKey);
            
            log.debug("Deleted all documents for knowledge base: {}", knowledgeBaseId);
        } catch (Exception e) {
            log.error("Failed to delete documents by knowledge base id: {}", knowledgeBaseId, e);
            throw new RuntimeException("Failed to delete documents by knowledge base id", e);
        }
    }
    
    @Override
    public void deleteByIds(List<String> ids) {
        for (String id : ids) {
            deleteById(id);
        }
    }
    
    @Override
    public boolean existsById(String id) {
        String key = KEY_PREFIX + id;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}

