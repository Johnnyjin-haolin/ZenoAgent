package com.aiagent.domain.tool;

import com.aiagent.domain.model.bo.AgentContext;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统工具：子 Agent（占位实现）
 * 用于启动子 Agent 处理复杂多步子任务
 *
 * @author aiagent
 */
@Slf4j
@Component
public class SubAgentTool implements SystemTool {

    private static final String TOOL_NAME = "system_sub_agent";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("启动子 Agent 处理复杂多步子任务（功能开发中）。")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("agentId", "子 Agent 的 ID")
                .addStringProperty("goal", "子任务目标描述")
                .required("agentId", "goal")
                .build())
            .build();
    }

    @Override
    public String execute(String jsonArguments, AgentContext context) {
        log.warn("SubAgentTool 尚未实现，arguments={}", jsonArguments);
        return "子 Agent 功能正在开发中，暂不可用。";
    }
}
