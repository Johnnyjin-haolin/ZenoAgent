package com.aiagent.infrastructure.repository;

import com.aiagent.domain.model.entity.McpServerEntity;
import com.aiagent.infrastructure.mapper.McpServerMapper;
import com.aiagent.common.util.UUIDGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * MCP 服务器配置数据访问层
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class McpServerRepository {

    private final McpServerMapper mapper;

    public McpServerEntity findById(String id) {
        return mapper.selectById(id);
    }

    public List<McpServerEntity> findAll() {
        return mapper.selectAll();
    }

    public List<McpServerEntity> findByScope(int scope) {
        return mapper.selectByScope(scope);
    }

    /**
     * 按 capability 列表查询 PERSONAL MCP（用于 Agent 运行时匹配）
     * 暂不过滤 ownerUserId，默认全部用户可见
     */
    public List<McpServerEntity> findByCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectByCapabilities(capabilities);
    }

    public List<McpServerEntity> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectByIds(ids);
    }

    public McpServerEntity save(McpServerEntity entity) {
        if (entity.getId() == null || entity.getId().isEmpty()) {
            entity.setId(UUIDGenerator.generate());
        }
        if (mapper.countById(entity.getId()) > 0) {
            mapper.update(entity);
        } else {
            mapper.insert(entity);
        }
        return entity;
    }

    public void updateEnabled(String id, boolean enabled) {
        mapper.updateEnabled(id, enabled ? 1 : 0);
    }

    public void deleteById(String id) {
        mapper.deleteById(id);
    }

    public boolean existsById(String id) {
        return mapper.countById(id) > 0;
    }

    public long count() {
        return findAll().size();
    }
}
