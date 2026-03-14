package com.aiagent.domain.model.bo;

import com.aiagent.application.AgentEventPublisher;
import com.aiagent.domain.action.ActionResult;
import com.aiagent.common.enums.AgentMode;
import com.aiagent.application.StreamingCallback;
import com.aiagent.domain.tool.todo.TodoItem;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.langchain4j.data.message.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 上下文
 * 维护Agent执行过程中的状态和历史
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * Agent配置ID
     */
    private String agentId;
    
    /**
     * 对话历史（存储DTO格式，用于序列化）
     * 注意：存储到Redis时使用MessageDTO，读取后转换回ChatMessage
     */
    private List<MessageBO> messageBOS;
    
    /**
     * 动作执行历史（按 ReACT 迭代轮次组织）
     * 外层 List：迭代轮次索引（第0轮、第1轮、第2轮...）
     * 内层 List：该轮迭代中执行的所有 ActionResult
     */
    private List<List<ActionResult>> actionExecutionHistory;

    /**
     * RAG检索历史
     */
    private List<Map<String, Object>> ragRetrieveHistory;
    
    /**
     * 迭代次数
     */
    private Integer iterations;
    
    /**
     * 模型ID
     */
    private String modelId;

    /**
     * 执行模式（自动/手动）
     */
    private AgentMode mode;
    
    /**
     * 启用的MCP分组列表
     */
    private List<String> enabledMcpGroups;
    
    /**
     * 启用的工具名称列表（为空则允许所有工具）
     */
    private List<String> enabledTools;
    
    /**
     * 知识库ID列表
     */
    private List<String> knowledgeIds;
    
    /**
     * 知识库信息映射（knowledgeId -> KnowledgeBase）
     * 在初始化时批量加载，避免在 ThinkingEngine 中重复查询
     */
    @JsonIgnore
    private Map<String, KnowledgeBase> knowledgeBaseMap;
    
    /**
     * 思考引擎配置
     * 用于控制提示词构建时的历史长度、截断等行为
     */
    @JsonIgnore
    private com.aiagent.api.dto.ThinkingConfig thinkingConfig;
    
    /**
     * RAG配置
     * 用于控制知识库检索的参数（检索数量、相似度阈值、内容长度限制等）
     */
    @JsonIgnore
    private com.aiagent.api.dto.RAGConfig ragConfig;

    /**
     * 用户名
     */
    private String username;
    
    /**
     * 请求ID（用于关联SSE事件，不需要序列化）
     */
    @JsonIgnore
    private transient String requestId;
    
    /**
     * 流式输出回调（运行时使用，不需要序列化）
     */
    @JsonIgnore
    private transient StreamingCallback streamingCallback;
    
    /**
     * 事件发布器（用于向前端发送进度事件，不需要序列化）
     * 通过 {@link AgentEventPublisher} 接口与传输协议解耦，当前实现为 {@code SseAgentEventPublisher}。
     */
    @JsonIgnore
    private transient AgentEventPublisher eventPublisher;
    
    /**
     * 初始 RAG 检索结果（仅在第一次请求时使用，不序列化）
     */
    @JsonIgnore
    private transient AgentKnowledgeResult initialRagResult;

    /**
     * 渐进式工具加载：本轮 LLM 通过 system_resolve_tools 工具请求加载的 MCP 工具名称集合
     * 工具执行后由 NativeFunctionCallingEngine 消费并清空，不需要序列化
     */
    @JsonIgnore
    private transient Set<String> activeMcpToolNames = new HashSet<>();

    /**
     * 本次推理执行过程记录（iterations / steps）
     * 由 FunctionCallingEngine 在 execute() 完成后写入，由 AgentServiceImpl 读取并持久化到
     * MessageEntity.metadata.executionProcess，供历史对话加载时还原 ReAct 迭代展示。
     * 运行时对象，不需要序列化到 Redis。
     */
    @JsonIgnore
    private transient ExecutionProcessRecord executionProcess;

    /**
     * Todo 任务清单（与会话绑定，随 AgentContext 持久化到 Redis）
     * 由 system_todo_write 工具读写，FunctionCallingEngine 在每轮构建系统提示词时注入
     */
    @Builder.Default
    private List<TodoItem> todos = new ArrayList<>();

    /**
     * 从 ChatMessage 列表设置消息（自动转换为DTO）
     * 注意：添加 @JsonIgnore 避免 Jackson 序列化此方法，防止与 messageDTOs 字段冲突
     */
    @JsonIgnore
    public void setMessages(List<ChatMessage> messages) {
        if (messages == null) {
            this.messageBOS = new ArrayList<>();
        } else {
            this.messageBOS = messages.stream()
                .map(MessageBO::from)
                .collect(Collectors.toList());
        }
    }
    
    /**
     * 获取 ChatMessage 列表（从DTO转换）
     * 注意：添加 @JsonIgnore 避免 Jackson 序列化此方法，防止与 messageDTOs 字段冲突
     */
    @JsonIgnore
    public List<ChatMessage> getMessages() {
        if (messageBOS == null) {
            return new ArrayList<>();
        }
        return messageBOS.stream()
            .map(MessageBO::toChatMessage)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * 添加消息
     */
    public void addMessage(ChatMessage message) {
        if (messageBOS == null) {
            messageBOS = new ArrayList<>();
        }
        MessageBO dto = MessageBO.from(message);
        if (dto != null) {
            messageBOS.add(dto);
        }
    }
}


