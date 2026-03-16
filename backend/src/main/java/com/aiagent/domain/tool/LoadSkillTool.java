package com.aiagent.domain.tool;

import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.skill.AgentSkill;
import com.aiagent.domain.skill.AgentSkillService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统工具：按需加载 Skill 全文内容
 *
 * <p>配合 Skill 渐进式加载机制使用：
 * System Prompt 中只注入每条 Skill 的摘要（summary），
 * LLM 判断需要使用某条 Skill 时，调用本工具传入 skillId，
 * 系统返回该 Skill 的完整内容（content），LLM 据此执行。
 *
 * @see com.aiagent.domain.skill.AgentSkill
 * @see AgentSkillService#getSkillContent(String)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadSkillTool implements SystemTool {

    private static final String TOOL_NAME = "system_load_skill";

    private final AgentSkillService agentSkillService;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("按需加载指定 Skill 的完整内容。" +
                "当你需要使用某条技能（Skill）时，先调用本工具传入技能 ID，" +
                "系统将返回该技能的完整操作指南，你再按照指南执行。")
            .parameters(JsonObjectSchema.builder()
                .addProperty("skillId", JsonStringSchema.builder()
                    .description("需要加载的 Skill ID，来自 System Prompt 中 Available Skills 列表的方括号内容，例如 \"skill-uuid-xxx\"")
                    .build())
                .required("skillId")
                .build())
            .build();
    }

    @Override
    public String execute(String jsonArguments, AgentContext context) {
        try {
            JSONObject args = JSON.parseObject(jsonArguments);
            String skillId = args.getString("skillId");

            if (skillId == null || skillId.isBlank()) {
                return "参数错误：skillId 不能为空，请提供有效的 Skill ID。";
            }

            AgentSkill skill = agentSkillService.getById(skillId);
            if (skill == null) {
                return "Skill 不存在或已被删除：" + skillId;
            }

            String content = skill.getContent();
            if (content == null || content.isBlank()) {
                return "Skill「" + skill.getName() + "」暂无完整内容，请直接根据摘要执行。";
            }

            log.info("加载 Skill 全文内容: skillId={}, name={}, contentLength={}",
                skillId, skill.getName(), content.length());

            return "# " + skill.getName() + "\n\n" + content;

        } catch (Exception e) {
            log.error("LoadSkillTool 执行失败", e);
            return "Skill 加载失败: " + e.getMessage();
        }
    }
}
