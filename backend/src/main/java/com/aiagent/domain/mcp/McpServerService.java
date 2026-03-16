package com.aiagent.domain.mcp;

import com.aiagent.api.dto.McpServerRequest;
import com.aiagent.api.dto.McpServerVO;
import com.aiagent.api.dto.McpToolInfo;
import com.aiagent.common.enums.McpScope;
import com.aiagent.domain.model.entity.McpServerEntity;
import com.aiagent.infrastructure.config.McpServerConfig;
import com.aiagent.infrastructure.external.mcp.McpClientFactory;
import com.aiagent.infrastructure.repository.McpServerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 服务器管理 Service
 * <p>
 * 负责 CRUD、工具列表拉取、连通性测试。
 * <p>
 * authHeader 字段现在存储 JSON 格式的键值对：
 *   GLOBAL  示例：{"Authorization":"Bearer sk-xxx","X-Tenant-Id":"t001"}
 *   PERSONAL示例：{"Authorization":"","X-Api-Key":""}  （值为空，运行时由浏览器补充）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerService {

    private final McpServerRepository repository;
    private final McpClientFactory mcpClientFactory;
    private final McpServerConfig mcpServerConfig;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ── 查询 ─────────────────────────────────────────────────────────────────

    public List<McpServerVO> listAll() {
        return repository.findAll().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public List<McpServerVO> listByScope(int scope) {
        return repository.findByScope(scope).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public McpServerVO getById(String id) {
        McpServerEntity entity = repository.findById(id);
        return entity == null ? null : toVO(entity);
    }

    /**
     * 按 serverMcpIds 批量查询（Agent 运行时使用）
     */
    public List<McpServerEntity> findByIds(List<String> ids) {
        return repository.findByIds(ids);
    }

    // ── 创建 / 更新 / 删除 ───────────────────────────────────────────────────

    public McpServerVO create(McpServerRequest req) {
        McpServerEntity entity = fromRequest(req);
        // PERSONAL 类型：storageType=local 的 Header value 已在前端清空，此处直接保存即可
        repository.save(entity);
        if (McpScope.GLOBAL.getValue() == entity.getScope()) {
            mcpServerConfig.reload();
        }
        return toVO(entity);
    }

    public McpServerVO update(String id, McpServerRequest req) {
        McpServerEntity existing = repository.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("MCP server not found: " + id);
        }
        applyRequest(existing, req);
        // PERSONAL 类型：storageType=local 的 Header value 已在前端清空，此处直接保存即可
        repository.save(existing);
        if (McpScope.GLOBAL.getValue() == existing.getScope()) {
            mcpServerConfig.reload();
        }
        return toVO(existing);
    }

    public void delete(String id) {
        McpServerEntity entity = repository.findById(id);
        if (entity != null) {
            repository.deleteById(id);
            if (McpScope.GLOBAL.getValue() == entity.getScope()) {
                mcpServerConfig.reload();
            }
            mcpClientFactory.removeClient(id);
        }
    }

    public void toggleEnabled(String id, boolean enabled) {
        repository.updateEnabled(id, enabled);
        McpServerEntity entity = repository.findById(id);
        if (entity != null && McpScope.GLOBAL.getValue() == entity.getScope()) {
            mcpServerConfig.reload();
        }
    }

    // ── 工具列表 & 连通性测试 ─────────────────────────────────────────────────

    public List<McpToolInfo> getTools(String serverId) {
        McpServerEntity entity = repository.findById(serverId);
        if (entity == null) {
            return new ArrayList<>();
        }
        if (McpScope.PERSONAL.getValue() == entity.getScope()) {
            log.info("PERSONAL MCP 工具列表由客户端管理，serverId={}", serverId);
            return new ArrayList<>();
        }
        return fetchToolsFromServer(entity);
    }

    public String testConnection(String serverId) {
        McpServerEntity entity = repository.findById(serverId);
        if (entity == null) {
            return "NOT_FOUND";
        }
        if (McpScope.PERSONAL.getValue() == entity.getScope()) {
            return "PERSONAL_CLIENT_SIDE";
        }
        try {
            List<McpToolInfo> tools = fetchToolsFromServer(entity);
            return "OK:" + tools.size();
        } catch (Exception e) {
            log.warn("MCP 连通性测试失败: serverId={}, error={}", serverId, e.getMessage());
            return "FAIL:" + e.getMessage();
        }
    }

    // ── JSON 工具方法 ─────────────────────────────────────────────────────────

    /**
     * 将 Map<String,String> 序列化为 JSON 字符串存入 authHeader 字段
     */
    public static String serializeAuthHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(headers);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将 authHeader 字段的 JSON 字符串反序列化为 Map
     */
    public static Map<String, String> parseAuthHeaders(String authHeaderJson) {
        if (authHeaderJson == null || authHeaderJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(authHeaderJson,
                    new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * PERSONAL 类型：将 authHeader JSON 中所有 value 置空（只保留 key）
     */
    private static String stripHeaderValues(String authHeaderJson) {
        Map<String, String> map = parseAuthHeaders(authHeaderJson);
        if (map.isEmpty()) {
            return null;
        }
        Map<String, String> stripped = new LinkedHashMap<>();
        map.forEach((k, v) -> stripped.put(k, ""));
        return serializeAuthHeaders(stripped);
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    private List<McpToolInfo> fetchToolsFromServer(McpServerEntity entity) {
        McpServerConfig.McpServerDefinition def = mcpServerConfig.findById(entity.getId());
        if (def == null) {
            log.warn("内存中未找到 MCP 服务器定义，可能尚未加载: id={}", entity.getId());
            return new ArrayList<>();
        }
        try {
            McpClient client = mcpClientFactory.getOrCreateClient(def);
            List<ToolSpecification> specs = client.listTools();
            return specs.stream()
                    .map(spec -> {
                        McpToolInfo tool = new McpToolInfo();
                        tool.setId(entity.getId() + ":" + spec.name());
                        tool.setName(spec.name());
                        tool.setDescription(spec.description());
                        tool.setServerId(entity.getId());
                        tool.setEnabled(true);
                        tool.setParameters(spec.parameters());
                        Map<String, Object> inputSchema = schemaToMap(spec.parameters());
                        log.debug("工具 [{}] parameters={}, inputSchema={}", spec.name(), spec.parameters(), inputSchema);
                        tool.setInputSchema(inputSchema);
                        return tool;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("拉取 MCP 工具列表失败: id={}", entity.getId(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 将 LangChain4j JsonObjectSchema 转换为标准 JSON Schema Map（手动递归，不依赖 Jackson）
     * 供前端展示工具参数使用
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaToMap(dev.langchain4j.model.chat.request.json.JsonObjectSchema schema) {
        if (schema == null) {
            return null;
        }
        return (Map<String, Object>) schemaElementToObject(schema);
    }

    private static Object schemaElementToObject(dev.langchain4j.model.chat.request.json.JsonSchemaElement element) {
        if (element == null) {
            return null;
        }
        if (element instanceof dev.langchain4j.model.chat.request.json.JsonObjectSchema) {
            dev.langchain4j.model.chat.request.json.JsonObjectSchema obj =
                    (dev.langchain4j.model.chat.request.json.JsonObjectSchema) element;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", "object");
            if (obj.description() != null) map.put("description", obj.description());
            if (obj.properties() != null && !obj.properties().isEmpty()) {
                Map<String, Object> props = new java.util.LinkedHashMap<>();
                obj.properties().forEach((k, v) -> props.put(k, schemaElementToObject(v)));
                map.put("properties", props);
            }
            if (obj.required() != null && !obj.required().isEmpty()) {
                map.put("required", obj.required());
            }
            if (obj.additionalProperties() != null) {
                map.put("additionalProperties", obj.additionalProperties());
            }
            if (obj.definitions() != null && !obj.definitions().isEmpty()) {
                Map<String, Object> defs = new java.util.LinkedHashMap<>();
                obj.definitions().forEach((k, v) -> defs.put(k, schemaElementToObject(v)));
                map.put("$defs", defs);
            }
            return map;
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonStringSchema) {
            dev.langchain4j.model.chat.request.json.JsonStringSchema s =
                    (dev.langchain4j.model.chat.request.json.JsonStringSchema) element;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", "string");
            if (s.description() != null) map.put("description", s.description());
            return map;
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonIntegerSchema) {
            dev.langchain4j.model.chat.request.json.JsonIntegerSchema s =
                    (dev.langchain4j.model.chat.request.json.JsonIntegerSchema) element;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", "integer");
            if (s.description() != null) map.put("description", s.description());
            return map;
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonNumberSchema) {
            dev.langchain4j.model.chat.request.json.JsonNumberSchema s =
                    (dev.langchain4j.model.chat.request.json.JsonNumberSchema) element;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", "number");
            if (s.description() != null) map.put("description", s.description());
            return map;
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonBooleanSchema) {
            dev.langchain4j.model.chat.request.json.JsonBooleanSchema s =
                    (dev.langchain4j.model.chat.request.json.JsonBooleanSchema) element;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", "boolean");
            if (s.description() != null) map.put("description", s.description());
            return map;
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonArraySchema) {
            dev.langchain4j.model.chat.request.json.JsonArraySchema s =
                    (dev.langchain4j.model.chat.request.json.JsonArraySchema) element;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", "array");
            if (s.description() != null) map.put("description", s.description());
            if (s.items() != null) map.put("items", schemaElementToObject(s.items()));
            return map;
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonEnumSchema) {
            dev.langchain4j.model.chat.request.json.JsonEnumSchema s =
                    (dev.langchain4j.model.chat.request.json.JsonEnumSchema) element;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", "string");
            if (s.description() != null) map.put("description", s.description());
            if (s.enumValues() != null) map.put("enum", s.enumValues());
            return map;
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonAnyOfSchema) {
            dev.langchain4j.model.chat.request.json.JsonAnyOfSchema s =
                    (dev.langchain4j.model.chat.request.json.JsonAnyOfSchema) element;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            if (s.description() != null) map.put("description", s.description());
            if (s.anyOf() != null) {
                java.util.List<Object> anyOf = new java.util.ArrayList<>();
                s.anyOf().forEach(e -> anyOf.add(schemaElementToObject(e)));
                map.put("anyOf", anyOf);
            }
            return map;
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonReferenceSchema) {
            dev.langchain4j.model.chat.request.json.JsonReferenceSchema s =
                    (dev.langchain4j.model.chat.request.json.JsonReferenceSchema) element;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            if (s.reference() != null) map.put("$ref", s.reference());
            return map;
        } else if (element instanceof dev.langchain4j.model.chat.request.json.JsonNullSchema) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", "null");
            return map;
        } else {
            // fallback
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", element.getClass().getSimpleName()
                    .replace("Json", "").replace("Schema", "").toLowerCase());
            return map;
        }
    }

    private McpServerVO toVO(McpServerEntity entity) {
        McpServerVO vo = new McpServerVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setScope(entity.getScope());
        vo.setOwnerUserId(entity.getOwnerUserId());
        vo.setConnectionType(entity.getConnectionType());
        vo.setEndpointUrl(entity.getEndpointUrl());

        // 解析 authHeader JSON：
        //   value 非空 → 脱敏为 "***"（前端据此识别为 cloud 存储，编辑时清空让用户重填）
        //   value 为空 → 保持 ""（前端据此识别为 local 存储）
        Map<String, String> parsed = parseAuthHeaders(entity.getAuthHeader());
        if (!parsed.isEmpty()) {
            Map<String, String> masked = new LinkedHashMap<>();
            parsed.forEach((k, v) -> masked.put(k, v != null && !v.isEmpty() ? "***" : ""));
            vo.setAuthHeaders(masked);
        }

        vo.setTimeoutMs(entity.getTimeoutMs());
        vo.setReadTimeoutMs(entity.getReadTimeoutMs());
        vo.setRetryCount(entity.getRetryCount());
        vo.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private McpServerEntity fromRequest(McpServerRequest req) {
        McpServerEntity entity = new McpServerEntity();
        applyRequest(entity, req);
        return entity;
    }

    private void applyRequest(McpServerEntity entity, McpServerRequest req) {
        if (req.getName() != null) entity.setName(req.getName());
        if (req.getDescription() != null) entity.setDescription(req.getDescription());
        if (req.getScope() != null) entity.setScope(req.getScope());
        if (req.getOwnerUserId() != null) entity.setOwnerUserId(req.getOwnerUserId());
        if (req.getConnectionType() != null) entity.setConnectionType(req.getConnectionType());
        if (req.getEndpointUrl() != null) entity.setEndpointUrl(req.getEndpointUrl());
        if (req.getAuthHeaders() != null) {
            // 编辑时：请求中 value 为空的 Header 保持原有值不变（前端留空=不修改密钥）
            // value 为 "" 且原实体中有对应值时，沿用原值
            Map<String, String> incoming = req.getAuthHeaders();
            Map<String, String> existing = parseAuthHeaders(entity.getAuthHeader());
            Map<String, String> merged = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, String> entry : incoming.entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if (v == null || v.isEmpty()) {
                    // 留空：沿用原有值（若原来没有则保持空）
                    merged.put(k, existing.getOrDefault(k, ""));
                } else {
                    merged.put(k, v);
                }
            }
            entity.setAuthHeader(serializeAuthHeaders(merged));
        }
        if (req.getExtraHeaders() != null) entity.setExtraHeaders(req.getExtraHeaders());
        if (req.getTimeoutMs() != null) entity.setTimeoutMs(req.getTimeoutMs());
        if (req.getReadTimeoutMs() != null) entity.setReadTimeoutMs(req.getReadTimeoutMs());
        if (req.getRetryCount() != null) entity.setRetryCount(req.getRetryCount());
        if (req.getEnabled() != null) entity.setEnabled(req.getEnabled() ? 1 : 0);
    }
}
