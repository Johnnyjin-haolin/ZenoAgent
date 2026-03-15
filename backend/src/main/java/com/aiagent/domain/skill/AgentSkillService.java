package com.aiagent.domain.skill;

import com.aiagent.domain.model.entity.AgentSkillEntity;
import com.aiagent.infrastructure.mapper.AgentSkillMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Skill 管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSkillService {

    private final AgentSkillMapper agentSkillMapper;
    private final ObjectMapper objectMapper;

    // ────────────────────────────────────────────────── CRUD

    public AgentSkill create(String name, String summary, String content, List<String> tags) {
        AgentSkillEntity entity = new AgentSkillEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(name);
        entity.setSummary(summary);
        entity.setContent(content);
        entity.setTags(serializeTags(tags));
        entity.setStatus("active");
        agentSkillMapper.insert(entity);
        log.info("创建 Skill: id={}, name={}", entity.getId(), entity.getName());
        return toDomain(entity);
    }

    public AgentSkill update(String id, String name, String summary, String content, List<String> tags) {
        AgentSkillEntity existing = agentSkillMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Skill 不存在: " + id);
        }
        AgentSkillEntity entity = new AgentSkillEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setSummary(summary);
        entity.setContent(content);
        entity.setTags(serializeTags(tags));
        agentSkillMapper.update(entity);
        log.info("更新 Skill: id={}", id);
        return toDomain(agentSkillMapper.selectById(id));
    }

    public void delete(String id) {
        agentSkillMapper.deleteById(id);
        log.info("删除 Skill: id={}", id);
    }

    public List<AgentSkill> listAll() {
        List<AgentSkillEntity> entities = agentSkillMapper.selectAll();
        List<AgentSkill> result = new ArrayList<>();
        for (AgentSkillEntity e : entities) {
            result.add(toDomain(e));
        }
        return result;
    }

    public AgentSkill getById(String id) {
        AgentSkillEntity entity = agentSkillMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    /**
     * 获取 Skill 全文内容（供 system_load_skill 工具调用）
     *
     * @param skillId Skill ID
     * @return Skill 全文内容，不存在时返回错误提示
     */
    public String getSkillContent(String skillId) {
        AgentSkillEntity entity = agentSkillMapper.selectById(skillId);
        if (entity == null) {
            return "Skill 不存在或已被删除: " + skillId;
        }
        return entity.getContent();
    }

    public long count() {
        return agentSkillMapper.count();
    }

    public List<AgentSkill> listPage(int offset, int pageSize) {
        List<AgentSkillEntity> entities = agentSkillMapper.selectPage(offset, pageSize);
        List<AgentSkill> result = new ArrayList<>();
        for (AgentSkillEntity e : entities) {
            result.add(toDomain(e));
        }
        return result;
    }

    // ────────────────────────────────────────────────── 转换

    public AgentSkill toDomain(AgentSkillEntity entity) {
        AgentSkill skill = new AgentSkill();
        skill.setId(entity.getId());
        skill.setName(entity.getName());
        skill.setSummary(entity.getSummary());
        skill.setContent(entity.getContent());
        skill.setStatus(entity.getStatus());
        skill.setCreateTime(entity.getCreateTime());
        skill.setUpdateTime(entity.getUpdateTime());
        if (entity.getTags() != null) {
            try {
                List<String> tags = objectMapper.readValue(entity.getTags(), new TypeReference<List<String>>() {});
                skill.setTags(tags);
            } catch (Exception e) {
                log.warn("反序列化 tags 失败: id={}", entity.getId());
            }
        }
        return skill;
    }

    private String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            log.warn("序列化 tags 失败", e);
            return "[]";
        }
    }
}
