package com.aiagent.repository.impl;

import com.aiagent.model.KnowledgeBase;
import com.aiagent.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Redis实现的知识库数据访问
 * 
 * @author aiagent
 */
@Slf4j
@Repository
public class RedisKnowledgeBaseRepository implements KnowledgeBaseRepository {
    
    private static final String KEY_PREFIX = "knowledge_base:";
    private static final String INDEX_KEY = "knowledge_base:index";
    private static final long TTL_DAYS = 365; // 1年过期时间
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void save(KnowledgeBase knowledgeBase) {
        try {
            String id = knowledgeBase.getId();
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("KnowledgeBase ID cannot be null or empty");
            }
            
            // 设置更新时间
            knowledgeBase.setUpdateTime(LocalDateTime.now());
            if (knowledgeBase.getCreateTime() == null) {
                knowledgeBase.setCreateTime(LocalDateTime.now());
            }
            
            String key = KEY_PREFIX + id;
            String json = objectMapper.writeValueAsString(knowledgeBase);
            
            // 保存数据
            redisTemplate.opsForValue().set(key, json, TTL_DAYS, TimeUnit.DAYS);
            
            // 添加到索引
            redisTemplate.opsForSet().add(INDEX_KEY, id);
            
            log.debug("Saved knowledge base: {}", id);
        } catch (Exception e) {
            log.error("Failed to save knowledge base", e);
            throw new RuntimeException("Failed to save knowledge base", e);
        }
    }
    
    @Override
    public Optional<KnowledgeBase> findById(String id) {
        try {
            String key = KEY_PREFIX + id;
            String json = redisTemplate.opsForValue().get(key);
            
            if (json == null || json.isEmpty()) {
                return Optional.empty();
            }
            
            KnowledgeBase knowledgeBase = objectMapper.readValue(json, KnowledgeBase.class);
            return Optional.of(knowledgeBase);
        } catch (Exception e) {
            log.error("Failed to find knowledge base by id: {}", id, e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<KnowledgeBase> findAll() {
        try {
            Set<String> ids = redisTemplate.opsForSet().members(INDEX_KEY);
            if (ids == null || ids.isEmpty()) {
                return new ArrayList<>();
            }
            
            List<KnowledgeBase> result = new ArrayList<>();
            for (String id : ids) {
                findById(id).ifPresent(result::add);
            }
            
            return result;
        } catch (Exception e) {
            log.error("Failed to find all knowledge bases", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void deleteById(String id) {
        try {
            String key = KEY_PREFIX + id;
            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(INDEX_KEY, id);
            log.debug("Deleted knowledge base: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete knowledge base: {}", id, e);
            throw new RuntimeException("Failed to delete knowledge base", e);
        }
    }
    
    @Override
    public boolean existsById(String id) {
        String key = KEY_PREFIX + id;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}

