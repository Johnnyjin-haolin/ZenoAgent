package com.aiagent.domain.tool;

import com.aiagent.domain.model.bo.AgentContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统工具：渐进式工具加载 - 按需解析 MCP 工具定义
 *
 * <p>当 MCP 工具数量超过阈值时，系统进入渐进式模式：
 * 只将工具的名称和描述注入 System Prompt（不含参数），LLM 判断需要使用某工具时，
 * 调用此工具传入工具名列表，系统在下一轮推理前将完整的 ToolSpecification 动态加入 tools 列表。
 *
 * @author aiagent
 */
@Slf4j
@Component
public class ResolveToolsTool implements SystemTool {

    private static final String TOOL_NAME = "system_resolve_tools";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("按需加载指定 MCP 工具的完整定义（参数 schema）。" +
                "当你判断需要使用某些工具但当前无法直接调用时（工具列表中提示\"按需加载\"），" +
                "先调用本工具传入工具名列表，下一轮推理中这些工具将出现在可调用列表中。")
            .parameters(JsonObjectSchema.builder()
                .addProperty("toolNames", JsonArraySchema.builder()
                    .description("需要加载的工具名称列表，例如 [\"list_servers\", \"create_instance\"]")
                    .items(JsonStringSchema.builder().build())
                    .build())
                .required("toolNames")
                .build())
            .build();
    }

    @Override
    public String execute(String jsonArguments, AgentContext context) {
        try {
            JSONObject args = JSON.parseObject(jsonArguments);
            List<String> toolNames = args.getList("toolNames", String.class);

            if (toolNames == null || toolNames.isEmpty()) {
                return "未指定工具名称，请提供 toolNames 列表。";
            }

            // 将请求的工具名写入 context，由 NativeFunctionCallingEngine 在本轮工具执行后消费
            if (context.getActiveMcpToolNames() == null) {
                context.setActiveMcpToolNames(new java.util.HashSet<>());
            }
            context.getActiveMcpToolNames().addAll(toolNames);

            log.info("渐进式工具加载请求，toolNames={}", toolNames);
            return "已记录工具加载请求：" + String.join(", ", toolNames) +
                "。下一轮推理中将加载这些工具的完整定义，届时可以直接调用。";

        } catch (Exception e) {
            log.error("ResolveToolsTool 执行失败", e);
            return "工具加载请求失败: " + e.getMessage();
        }
    }
}
