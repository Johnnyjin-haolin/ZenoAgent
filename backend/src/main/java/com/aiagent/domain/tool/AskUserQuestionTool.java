package com.aiagent.domain.tool;

import com.aiagent.common.constant.AgentConstants;
import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.infrastructure.external.mcp.UserAnswerManager;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 系统工具：向用户发起交互提问
 * 支持 SINGLE_SELECT / MULTI_SELECT / INPUT / PREVIEW 四种形式
 * 选项类工具自动追加「其他」选项
 *
 * @author aiagent
 */
@Slf4j
@Component
public class AskUserQuestionTool implements SystemTool {

    private static final String TOOL_NAME = "system_ask_user_question";
    private static final long ANSWER_TIMEOUT_MS = 5 * 60_000L;

    @Autowired
    private UserAnswerManager userAnswerManager;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("向用户发起提问以获取必要信息。支持四种形式：" +
                "SINGLE_SELECT（单选）、MULTI_SELECT（多选）、INPUT（自由输入）、PREVIEW（预览确认）。" +
                "系统会自动为选项补充「其他」选项。当任务需要用户提供选择或补充信息时调用。")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("question", "向用户展示的问题正文")
                .addStringProperty("questionType", "提问类型：SINGLE_SELECT / MULTI_SELECT / INPUT / PREVIEW")
                .addProperty("options", JsonArraySchema.builder()
                    .description("选项列表（SINGLE_SELECT/MULTI_SELECT 时填写），系统自动追加「其他」")
                    .items(JsonStringSchema.builder().build())
                    .build())
                .addStringProperty("previewContent", "PREVIEW 模式下展示给用户预览的内容")
                .required("question", "questionType")
                .build())
            .build();
    }

    @Override
    public String execute(String jsonArguments, AgentContext context) {
        try {
            JSONObject args = JSON.parseObject(jsonArguments);
            String question = args.getString("question");
            String questionType = args.getString("questionType");
            List<String> options = args.getList("options", String.class);
            String previewContent = args.getString("previewContent");

            // 自动追加「其他」选项
            if (options != null && !options.contains("其他")) {
                options = new ArrayList<>(options);
                options.add("其他");
            }

            String questionId = UUID.randomUUID().toString();
            userAnswerManager.register(questionId);

            // 通过 SSE 事件推送问题给前端
            if (context.getEventPublisher() != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("questionId", questionId);
                data.put("question", question);
                data.put("questionType", questionType);
                if (options != null) {
                    data.put("options", options);
                }
                if (previewContent != null) {
                    data.put("previewContent", previewContent);
                }

                context.getEventPublisher().accept(
                    com.aiagent.api.dto.AgentEventData.builder()
                        .event(AgentConstants.EVENT_AGENT_ASK_USER_QUESTION)
                        .data(data)
                        .build()
                );
            }

            log.info("向用户提问，questionId={}, type={}, question={}", questionId, questionType, question);

            // 阻塞等待用户回答
            String answer = userAnswerManager.waitForAnswer(questionId, ANSWER_TIMEOUT_MS);

            if (answer == null) {
                return "用户未在规定时间内回答（超时）。";
            }

            log.info("收到用户回答，questionId={}, answer={}", questionId, answer);
            return "用户回答：" + answer;

        } catch (Exception e) {
            log.error("向用户提问失败", e);
            return "向用户提问失败: " + e.getMessage();
        }
    }
}
