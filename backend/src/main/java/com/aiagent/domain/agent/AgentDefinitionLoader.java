package com.aiagent.domain.agent;

import com.aiagent.api.dto.RAGConfig;
import com.aiagent.domain.model.entity.AgentEntity;
import com.aiagent.infrastructure.mapper.AgentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agent 定义加载器
 * <p>
 * 启动时将 classpath:config/agents.yaml 中的内置 Agent upsert 入 DB，
 * 之后所有 Agent 定义统一从数据库读取，支持用户自定义。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDefinitionLoader {

    private final AgentMapper agentMapper;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        syncBuiltinAgentsToDb();
    }

    /**
     * 将 agents.yaml 中的内置 Agent 同步到数据库（不存在则插入，已存在则跳过）
     */
    private void syncBuiltinAgentsToDb() {
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            yamlMapper.findAndRegisterModules();
            InputStream is = getClass().getClassLoader().getResourceAsStream("config/agents.yaml");
            if (is == null) {
                log.warn("agents.yaml 未找到，跳过内置 Agent 初始化");
                return;
            }
            AgentsConfig config = yamlMapper.readValue(is, AgentsConfig.class);
            if (config == null || config.getAgents() == null) {
                return;
            }
            for (AgentDefinition def : config.getAgents()) {
                int exists = agentMapper.countById(def.getId());
                if (exists > 0) {
                    log.debug("内置 Agent 已存在，跳过: id={}", def.getId());
                    continue;
                }
                AgentEntity entity = toEntity(def, true);
                agentMapper.insert(entity);
                log.info("内置 Agent 初始化入库: id={}, name={}", def.getId(), def.getName());
            }
            log.info("内置 Agent 同步完成");
        } catch (Exception e) {
            log.error("内置 Agent 同步失败", e);
        }
    }

    /**
     * 从数据库根据 ID 获取 AgentDefinition
     */
    public AgentDefinition getById(String agentId) {
        AgentEntity entity = agentMapper.selectById(agentId);
        return entity == null ? null : toDomain(entity);
    }

    /**
     * 从数据库获取所有可用的 AgentDefinition（内置优先）
     */
    public List<AgentDefinition> getAll() {
        List<AgentEntity> entities = agentMapper.selectAll();
        List<AgentDefinition> result = new ArrayList<>();
        for (AgentEntity e : entities) {
            result.add(toDomain(e));
        }
        return result;
    }

    // ------------------------------------------------------------------ 转换

    public AgentEntity toEntity(AgentDefinition def, boolean isBuiltin) {
        AgentEntity entity = new AgentEntity();
        entity.setId(def.getId() != null ? def.getId() : UUID.randomUUID().toString());
        entity.setName(def.getName());
        entity.setDescription(def.getDescription());
        entity.setSystemPrompt(def.getSystemPrompt());
        entity.setIsBuiltin(isBuiltin ? 1 : 0);
        entity.setStatus("active");

        try {
            entity.setToolsConfig(jsonMapper.writeValueAsString(def.getTools()));
        } catch (Exception e) {
            log.warn("序列化 toolsConfig 失败: {}", e.getMessage());
            entity.setToolsConfig("{}");
        }

        if (def.getContextConfig() != null) {
            try {
                entity.setContextConfig(jsonMapper.writeValueAsString(def.getContextConfig()));
            } catch (Exception e) {
                log.warn("序列化 contextConfig 失败: {}", e.getMessage());
            }
        }

        if (def.getRagConfig() != null) {
            try {
                entity.setRagConfig(jsonMapper.writeValueAsString(def.getRagConfig()));
            } catch (Exception e) {
                log.warn("序列化 ragConfig 失败: {}", e.getMessage());
            }
        }

        return entity;
    }

    public AgentDefinition toDomain(AgentEntity entity) {
        AgentDefinition def = new AgentDefinition();
        def.setId(entity.getId());
        def.setName(entity.getName());
        def.setDescription(entity.getDescription());
        def.setSystemPrompt(entity.getSystemPrompt());

        if (entity.getToolsConfig() != null) {
            try {
                AgentDefinition.ToolsConfig tools =
                        jsonMapper.readValue(entity.getToolsConfig(), AgentDefinition.ToolsConfig.class);
                def.setTools(tools);
            } catch (Exception e) {
                log.warn("反序列化 toolsConfig 失败: id={}, err={}", entity.getId(), e.getMessage());
            }
        }

        if (entity.getContextConfig() != null) {
            try {
                AgentDefinition.ContextConfig ctxCfg =
                        jsonMapper.readValue(entity.getContextConfig(), AgentDefinition.ContextConfig.class);
                def.setContextConfig(ctxCfg);
            } catch (Exception e) {
                log.warn("反序列化 contextConfig 失败: id={}, err={}", entity.getId(), e.getMessage());
            }
        }

        if (entity.getRagConfig() != null) {
            try {
                RAGConfig ragCfg = jsonMapper.readValue(entity.getRagConfig(), RAGConfig.class);
                def.setRagConfig(ragCfg);
            } catch (Exception e) {
                log.warn("反序列化 ragConfig 失败: id={}, err={}", entity.getId(), e.getMessage());
            }
        }

        return def;
    }

    /** YAML 根节点映射类 */
    public static class AgentsConfig {
        private List<AgentDefinition> agents;
        public List<AgentDefinition> getAgents() { return agents; }
        public void setAgents(List<AgentDefinition> agents) { this.agents = agents; }
    }
}
