package com.aiagent.domain.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 定义加载器
 * 启动时从 classpath:config/agents.yaml 加载所有 AgentDefinition
 *
 * @author aiagent
 */
@Slf4j
@Component
public class AgentDefinitionLoader {

    private final Map<String, AgentDefinition> agentMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.findAndRegisterModules();
            InputStream is = getClass().getClassLoader().getResourceAsStream("config/agents.yaml");
            if (is == null) {
                log.warn("agents.yaml 未找到，跳过 Agent 定义加载");
                return;
            }
            AgentsConfig config = mapper.readValue(is, AgentsConfig.class);
            if (config != null && config.getAgents() != null) {
                for (AgentDefinition def : config.getAgents()) {
                    agentMap.put(def.getId(), def);
                    log.info("加载 Agent 定义: id={}, name={}", def.getId(), def.getName());
                }
            }
            log.info("Agent 定义加载完成，共 {} 个", agentMap.size());
        } catch (Exception e) {
            log.error("加载 agents.yaml 失败", e);
        }
    }

    /**
     * 根据 agentId 获取 AgentDefinition，不存在时返回 null
     */
    public AgentDefinition getById(String agentId) {
        return agentMap.get(agentId);
    }

    /**
     * 获取所有已加载的 AgentDefinition
     */
    public List<AgentDefinition> getAll() {
        return new ArrayList<>(agentMap.values());
    }

    /** YAML 根节点映射类 */
    public static class AgentsConfig {
        private List<AgentDefinition> agents;
        public List<AgentDefinition> getAgents() { return agents; }
        public void setAgents(List<AgentDefinition> agents) { this.agents = agents; }
    }
}
