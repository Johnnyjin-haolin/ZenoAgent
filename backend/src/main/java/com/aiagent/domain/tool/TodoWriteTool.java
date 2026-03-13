package com.aiagent.domain.tool;

import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.domain.tool.todo.TodoItem;
import com.aiagent.domain.tool.todo.TodoItem.TodoStatus;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 系统工具：任务记事本（Todo List）
 *
 * <p>为 Agent 提供复杂任务拆分与追踪能力。当用户指令较为复杂、可拆分为多个独立步骤时，
 * Agent 应主动创建 Todo 清单，并在完成每项任务后及时更新状态。
 *
 * <p>支持三种操作：
 * <ul>
 *   <li>{@code create}：批量创建 Todo 条目</li>
 *   <li>{@code update}：更新指定 Todo 的状态（completed / cancelled）</li>
 *   <li>{@code list}：查询当前所有 Todo 条目</li>
 * </ul>
 *
 * @author aiagent
 */
@Slf4j
@Component
public class TodoWriteTool implements SystemTool {

    private static final String TOOL_NAME = "system_todo_write";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
            .name(TOOL_NAME)
            .description(
                "任务记事本工具。当用户指令较为复杂、且可拆分为多个相对独立的步骤时，使用此工具管理任务清单。\n" +
                "- 仅在任务确实复杂（3步以上）且各步骤相对独立时才创建 Todo，简单任务无需创建。\n" +
                "- 每完成一个步骤后，立即调用 update 将对应 Todo 标记为 completed。\n" +
                "- 发现某步骤不再需要执行时，将其标记为 cancelled。\n" +
                "支持三种操作（action）：\n" +
                "  create - 批量创建 Todo 条目，通过 todos 参数传入任务列表\n" +
                "  update - 更新指定 Todo 的状态，通过 updates 参数传入变更列表\n" +
                "  list   - 查询当前所有 Todo 条目（正常情况下无需主动调用，系统提示词中已包含）"
            )
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("action",
                    "操作类型：create（创建）/ update（更新状态）/ list（查询列表）")
                .addProperty("todos", JsonArraySchema.builder()
                    .description("create 操作时必填：要创建的任务列表")
                    .items(JsonObjectSchema.builder()
                        .addStringProperty("content", "任务内容描述")
                        .addProperty("priority", JsonIntegerSchema.builder()
                            .description("优先级：1（高）/ 2（中）/ 3（低），默认为 2")
                            .build())
                        .required("content")
                        .build())
                    .build())
                .addProperty("updates", JsonArraySchema.builder()
                    .description("update 操作时必填：要更新状态的任务列表")
                    .items(JsonObjectSchema.builder()
                        .addStringProperty("id", "要更新的 Todo ID")
                        .addStringProperty("status",
                            "目标状态：completed（已完成）/ cancelled（已取消）")
                        .required("id", "status")
                        .build())
                    .build())
                .required("action")
                .build())
            .build();
    }

    @Override
    public String execute(String jsonArguments, AgentContext context) {
        try {
            JSONObject args = JSON.parseObject(jsonArguments);
            String action = args.getString("action");
            if (action == null || action.isBlank()) {
                return "参数错误：action 不能为空，可选值：create / update / list";
            }

            return switch (action.toLowerCase().trim()) {
                case "create" -> handleCreate(args, context);
                case "update" -> handleUpdate(args, context);
                case "list"   -> handleList(context);
                default       -> "未知操作类型: " + action + "，可选值：create / update / list";
            };

        } catch (Exception e) {
            log.error("TodoWriteTool 执行失败", e);
            return "Todo 操作失败: " + e.getMessage();
        }
    }

    private String handleCreate(JSONObject args, AgentContext context) {
        JSONArray todosJson = args.getJSONArray("todos");
        if (todosJson == null || todosJson.isEmpty()) {
            return "参数错误：create 操作需要提供 todos 列表";
        }

        List<TodoItem> created = new ArrayList<>();
        List<TodoItem> todos = context.getTodos();

        for (int i = 0; i < todosJson.size(); i++) {
            JSONObject item = todosJson.getJSONObject(i);
            String content = item.getString("content");
            if (content == null || content.isBlank()) {
                continue;
            }
            int priority = item.getIntValue("priority", 2);
            if (priority < 1 || priority > 3) {
                priority = 2;
            }

            TodoItem todoItem = TodoItem.builder()
                .id(UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .content(content)
                .status(TodoStatus.pending)
                .priority(priority)
                .createdAt(System.currentTimeMillis())
                .build();

            todos.add(todoItem);
            created.add(todoItem);
        }

        if (created.isEmpty()) {
            return "未创建任何 Todo，请检查 todos 参数格式";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("成功创建 ").append(created.size()).append(" 个 Todo：\n");
        for (TodoItem item : created) {
            sb.append("  [P").append(item.getPriority()).append("] ")
              .append("(id: ").append(item.getId()).append(") ")
              .append(item.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    private String handleUpdate(JSONObject args, AgentContext context) {
        JSONArray updatesJson = args.getJSONArray("updates");
        if (updatesJson == null || updatesJson.isEmpty()) {
            return "参数错误：update 操作需要提供 updates 列表";
        }

        List<TodoItem> todos = context.getTodos();
        List<String> successIds = new ArrayList<>();
        List<String> notFoundIds = new ArrayList<>();

        for (int i = 0; i < updatesJson.size(); i++) {
            JSONObject update = updatesJson.getJSONObject(i);
            String id = update.getString("id");
            String statusStr = update.getString("status");

            if (id == null || statusStr == null) {
                continue;
            }

            TodoStatus newStatus;
            try {
                newStatus = TodoStatus.valueOf(statusStr.toLowerCase().trim());
            } catch (IllegalArgumentException e) {
                return "无效的状态值: " + statusStr + "，可选值：completed / cancelled";
            }

            boolean found = false;
            for (TodoItem todo : todos) {
                if (todo.getId().equals(id)) {
                    todo.setStatus(newStatus);
                    successIds.add(id);
                    found = true;
                    break;
                }
            }
            if (!found) {
                notFoundIds.add(id);
            }
        }

        StringBuilder sb = new StringBuilder();
        if (!successIds.isEmpty()) {
            sb.append("成功更新 ").append(successIds.size()).append(" 个 Todo（id: ")
              .append(String.join(", ", successIds)).append("）");
        }
        if (!notFoundIds.isEmpty()) {
            if (!sb.isEmpty()) sb.append("；");
            sb.append("以下 id 未找到: ").append(String.join(", ", notFoundIds));
        }
        return sb.toString();
    }

    private String handleList(AgentContext context) {
        List<TodoItem> todos = context.getTodos();
        if (todos.isEmpty()) {
            return "当前没有任何 Todo 条目。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("当前 Todo 清单（共 ").append(todos.size()).append(" 条）：\n");
        for (TodoItem item : todos) {
            String statusMark = switch (item.getStatus()) {
                case completed -> "[x]";
                case cancelled -> "[-]";
                default        -> "[ ]";
            };
            sb.append("  ").append(statusMark)
              .append(" [P").append(item.getPriority()).append("]")
              .append(" (id: ").append(item.getId()).append(") ")
              .append(item.getContent()).append("\n");
        }
        return sb.toString().trim();
    }
}
