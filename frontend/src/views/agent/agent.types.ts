/**
 * AI Agent 相关类型定义
 * @date 2025-11-30
 */

// ─────────────────────────────────────────────────────────────────────────────
// Agent 定义级别配置（持久化到数据库）
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Agent 上下文行为配置（对应后端 AgentDefinition.ContextConfig）
 */
export interface AgentContextConfig {
  /** 从数据库加载的历史消息条数上限（默认 20） */
  historyMessageLoadLimit?: number;
  /** 最大工具调用轮数（默认 8） */
  maxToolRounds?: number;
}

/** Agent 上下文配置的默认值 */
export const DEFAULT_CONTEXT_CONFIG: Required<AgentContextConfig> = {
  historyMessageLoadLimit: 20,
  maxToolRounds: 8,
};

/**
 * RAG 检索参数（AgentDefinition.ragConfig 的映射）
 */
export interface RAGConfig {
  /** 最大检索文档数量（默认 3） */
  maxResults?: number;
  /** 最小相似度分数（默认 0.5，范围 0-1） */
  minScore?: number;
  /** 单个文档最大字符数（null 表示不限制） */
  maxDocumentLength?: number | null;
  /** 所有文档总内容最大字符数（null 表示不限制） */
  maxTotalContentLength?: number | null;
}

/** RAG 配置的默认值 */
export const DEFAULT_RAG_CONFIG: RAGConfig = {
  maxResults: 3,
  minScore: 0.5,
  maxDocumentLength: 1000,
  maxTotalContentLength: 3000,
};

/** Agent 定义中的 RAG 配置（与 RAGConfig 类型共用） */
export type AgentRagConfig = RAGConfig;

/**
 * Agent Skill 目录树节点
 */
export interface SkillTreeNode {
  /** 节点 ID（在 Agent 树内唯一） */
  id: string;
  /** 节点显示名称 */
  label: string;
  /** 是否启用 */
  enabled: boolean;
  /** 引用的 Skill ID（叶节点专用，目录节点为 undefined） */
  skillId?: string;
  /** 子节点列表 */
  children: SkillTreeNode[];
}

/**
 * Agent 工具选择配置（对应后端 AgentDefinition.ToolsConfig）
 */
export interface AgentToolsConfig {
  /** GLOBAL MCP 服务器 ID 列表（服务端执行，scope=0） */
  serverMcpIds?: string[];
  /** PERSONAL MCP 能力标签列表（客户端执行，scope=1），如 ['github', 'notion'] */
  personalMcpCapabilities?: string[];
  /** 系统内置工具名称列表 */
  systemTools?: string[];
  /** 绑定的知识库 ID 列表 */
  knowledgeIds?: string[];
}

/**
 * Agent 定义（用户可配置的 Agent）
 */
export interface AgentDefinition {
  /** Agent ID */
  id: string;
  /** Agent 名称 */
  name: string;
  /** 描述 */
  description?: string;
  /** 系统提示词 */
  systemPrompt?: string;
  /** 工具选择配置 */
  tools?: AgentToolsConfig;
  /** 上下文行为配置 */
  contextConfig?: AgentContextConfig;
  /** RAG 检索配置 */
  ragConfig?: AgentRagConfig;
  /** Agent 私有 Skill 目录树 */
  skillTree?: SkillTreeNode[];
  /** 是否内置 */
  builtin: boolean;
  /** 状态 */
  status?: string;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

/**
 * 创建/更新 Agent 的请求体
 */
export interface AgentDefinitionRequest {
  name: string;
  description?: string;
  systemPrompt?: string;
  tools?: AgentToolsConfig;
  contextConfig?: AgentContextConfig;
  ragConfig?: AgentRagConfig;
  /** Agent 私有 Skill 目录树 */
  skillTree?: SkillTreeNode[];
}

/**
 * 系统工具信息
 */
export interface SystemToolInfo {
  name: string;
  description: string;
}

/**
 * PERSONAL MCP 工具 Schema
 * 由前端在发送消息前 prefetch 各 PERSONAL MCP 服务器的工具列表，
 * 将真实工具 schema 随 AgentRequest 上传到后端。
 */
export interface PersonalMcpToolSchema {
  /** 对应的 PERSONAL MCP 服务器 ID（SSE 下发时用于前端路由） */
  serverId: string;
  /** 真实工具名（如 search_bailian、list_files） */
  toolName: string;
  /** 工具描述（来自 MCP tools/list 响应） */
  description: string;
  /**
   * JSON Schema 参数定义（兼容 OpenAI function calling 格式）
   * 来自 MCP tools/list 响应中的 inputSchema 字段
   */
  inputSchema?: Record<string, unknown>;
}

/**
* Agent 请求参数
*/
export interface AgentRequest {
/** 用户输入内容 */
content: string;
/** 会话ID（可选，用于上下文关联） */
conversationId?: string;
/** 指定使用的 Agent ID（可选） */
agentId?: string;
/** 指定使用的模型ID（可选） */
modelId?: string;
/** 关联的知识库ID列表 */
knowledgeIds?: string[];
/** 启用的工具名称（支持通配符） */
enabledTools?: string[];
/** GLOBAL MCP 服务器 ID 列表（为空则使用 Agent 默认配置） */
serverMcpIds?: string[];
/**
 * PERSONAL MCP 工具 Schema 列表（前端 prefetch 后随请求上传）
 * 后端直接用这些 schema 构造真实 ToolSpecification 交给 LLM。
 * prefetch 失败的服务器工具不会出现在此列表，该服务器的工具本次对话不可用。
 */
personalMcpTools?: PersonalMcpToolSchema[];
/** 执行模式：AUTO-自动 / MANUAL-手动 */
mode?: 'AUTO' | 'MANUAL';
/** 自定义上下文参数 */
context?: Record<string, any>;
}

/**
 * Agent 向用户提问的问题类型
 */
export type UserQuestionType = 'SINGLE_SELECT' | 'MULTI_SELECT' | 'INPUT' | 'PREVIEW';

/**
 * Agent 向用户发起的问题（由 system_ask_user_question 工具触发）
 */
export interface UserQuestion {
  /** 问题唯一ID（提交答案时回传） */
  questionId: string;
  /** 问题内容 */
  question: string;
  /** 问题类型 */
  questionType: UserQuestionType;
  /** 选项列表（SINGLE_SELECT / MULTI_SELECT 时有值） */
  options?: string[];
  /** 预览内容（PREVIEW 时有值） */
  previewContent?: string;
}

/**
 * Agent 事件类型
 */
export type AgentEventType =
  | 'agent:start'           // 任务开始
  | 'agent:thinking'        // AI 思考中
  | 'agent:thinking_delta'  // 思考内容流式片段
  | 'agent:planning'        // 正在规划
  | 'agent:tool_executing'  // 正在执行工具
  | 'agent:rag_querying'    // 正在查询知识库
  | 'agent:generating'      // 正在生成回复
  | 'agent:observing'       // 正在观察结果
  | 'agent:reflecting'      // 正在反思
  | 'agent:model_selected'  // 模型已选择
  | 'agent:rag_retrieve'    // RAG 检索中
  | 'agent:tool_call'       // 工具调用中
  | 'agent:tool_result'     // 工具执行结果
  | 'agent:message'         // 流式内容
  | 'agent:iteration_start' // 迭代开始（tool_call 事件触发时自动创建）
  | 'agent:iteration_end'   // 迭代结束（tool_result 后触发）
  | 'agent:status:analyzing'          // 正在分析任务和用户意图
  | 'agent:status:thinking_process'     // 思考过程
  | 'agent:status:planning'             // 正在规划
  | 'agent:status:rag_querying'         // 正在查询知识库
  | 'agent:status:tool_executing_single' // 单个工具执行
  | 'agent:status:tool_executing_batch'  // 批量工具执行
  | 'agent:status:retrying'              // 格式重试中
  | 'agent:ask_user_question'    // Agent 向用户提问（AskUserQuestion 工具触发）
  | 'agent:personal_tool_call'   // PERSONAL MCP 工具调用（客户端执行）
  | 'agent:stream_complete'      // 流式输出完成
  | 'agent:complete'             // 任务完成
  | 'agent:error';               // 错误发生

/**
 * Agent 事件数据
 */
export interface AgentEvent {
  /** 请求ID */
  requestId: string;
  /** 事件类型 */
  event?: AgentEventType;
  /** 消息内容 */
  message?: string;
  /** 状态文本（用于显示当前状态） */
  statusText?: string;
  /** 流式内容（用于 agent:message） */
  content?: string;
  /** 附加数据 */
  data?: any;
  /** 错误信息 */
  error?: string;
  /** 时间戳 */
  timestamp?: number;
  /** Token 使用量 */
  totalTokens?: number;
  /** 耗时（毫秒） */
  duration?: number;
  /** 会话ID */
  conversationId?: string;
  /** 主题ID */
  topicId?: string;
}

/**
 * 模型信息
 */
export interface ModelInfo {
  /** 模型ID */
  id: string;
  /** 模型名称 */
  name?: string;
  /** 展示名称 */
  displayName: string;
  /** 模型描述 */
  description: string;
  /** 模型提供商 */
  provider?: string;
  /** 模型类型（CHAT: 对话模型, EMBEDDING: 向量模型） */
  type?: string;
  /** 图标（emoji 或图标类名） */
  icon?: string;
  /** 排序 */
  sort: number;
  /** 是否为默认选项 */
  isDefault: boolean;
}

/**
 * 知识库信息
 */
export interface KnowledgeInfo {
  /** 知识库ID */
  id: string;
  /** 知识库名称 */
  name: string;
  /** 描述 */
  description?: string;
  /** 图标 */
  icon?: string;
  /** 文档数量 */
  documentCount?: number;
}

/**
 * 工具调用记录
 */
export interface ToolCall {
  /** 调用ID */
  id?: string;
  /** 工具名称 */
  name: string;
  /** 调用参数 */
  params: Record<string, any>;
  /** 执行结果 */
  result?: any;
  /** 执行状态 */
  status?: 'pending' | 'success' | 'error';
  /** 错误信息 */
  error?: string;
  /** 耗时 */
  duration?: number;
}

/**
 * RAG 检索结果
 */
export interface RagResult {
  /** 文档内容 */
  content: string;
  /** 相似度分数 */
  score?: number;
  /** 来源 */
  source?: string;
  /** 知识库ID */
  knowledgeId?: string;
}

/**
 * 执行步骤类型
 */
export type ProcessStepType = 
  | 'thinking'      // 思考与规划
  | 'rag_retrieve'  // 检索知识库
  | 'tool_call'     // 工具调用
  | 'generating'    // 生成回答
  | 'complete';     // 完成

/**
 * 执行步骤状态
 */
export type ProcessStepStatus = 
  | 'waiting'   // 等待中
  | 'running'   // 执行中
  | 'success'   // 成功
  | 'error'     // 失败
  | 'skipped';  // 跳过

/**
 * 规划步骤信息
 */
export interface PlanStep {
  /** 步骤ID */
  stepId: string;
  /** 步骤编号 */
  stepNumber: number;
  /** 描述 */
  description: string;
  /** 类型 */
  type: string;
}

/**
 * 规划信息
 */
export interface PlanInfo {
  /** 规划ID */
  planId?: string;
  /** 任务类型 */
  taskType?: string;
  /** 规划步骤列表 */
  steps?: PlanStep[];
  /** 变量 */
  variables?: Record<string, any>;
}

/**
 * 子步骤信息
 */
export interface ProcessSubStep {
  /** 子步骤ID */
  id: string;
  /** 描述文本 */
  message: string;
  /** 时间戳 */
  timestamp: number;
  /** 是否包含规划信息 */
  hasPlan?: boolean;
  /** 步骤编号信息（如"步骤 1/3"） */
  stepProgress?: {
    current: number;
    total: number;
    description: string;
  };
}

/**
 * 执行步骤
 */
export interface ProcessStep {
  /** 步骤ID */
  id: string;
  /** 步骤类型 */
  type: ProcessStepType;
  /** 步骤名称 */
  name: string;
  /** 步骤状态 */
  status: ProcessStepStatus;
  /** 开始时间 */
  startTime?: number;
  /** 结束时间 */
  endTime?: number;
  /** 耗时（毫秒） */
  duration?: number;
  /** 是否展开详情 */
  expanded?: boolean;
  /** 子步骤列表（用于思考步骤的子步骤） */
  subSteps?: ProcessSubStep[];
  /** 规划信息（用于展示执行计划） */
  planInfo?: PlanInfo;
  /** 当前步骤进度（如"步骤 1/3"） */
  stepProgress?: {
    current: number;
    total: number;
  };
  /** 附加信息 */
  metadata?: {
    /** 工具名称（tool_call 类型） */
    toolName?: string;
    /** 工具参数 */
    toolParams?: Record<string, any>;
    /** 工具结果 */
    toolResult?: any;
    /** 工具错误 */
    toolError?: string;
    /** 检索结果数量（rag_retrieve 类型） */
    retrieveCount?: number;
    /** RAG 结果列表 */
    ragResults?: RagResult[];
    /** 平均分数 */
    avgScore?: number;
    /** 错误信息 */
    errorMessage?: string;
    /** 其他信息 */
    [key: string]: any;
  };
}

/**
 * ReAct 迭代信息（动态步骤版本）
 */
export interface ReActIteration {
  /** 迭代编号（1-based）*/
  iterationNumber: number;
  
  /** 动态步骤列表 */
  steps: ProcessStep[];
  
  /** 迭代状态 */
  status: 'running' | 'completed';
  
  /** 开始时间 */
  startTime: number;
  
  /** 结束时间 */
  endTime?: number;
  
  /** 总耗时 */
  totalDuration?: number;
  
  /** 是否折叠 */
  collapsed: boolean;
  
  /** 是否继续迭代（false 表示最后一轮） */
  shouldContinue?: boolean;
  
  /** 终止原因 */
  terminationReason?: string;
  
  /** 终止消息 */
  terminationMessage?: string;
}

/**
 * 执行过程
 */
export interface ExecutionProcess {
  /** ReAct 迭代列表 */
  iterations: ReActIteration[];
  /** 总耗时 */
  totalDuration?: number;
  /** 完成迭代数 */
  completedCount?: number;
  /** 流式输出是否已开始 */
  streamingStarted?: boolean;
}

/**
 * Agent 消息
 */
export interface AgentMessage {
  /** 消息ID */
  id: string;
  /**
   * 角色
   * - 'question': Agent 通过 AskUserQuestion 工具向用户发起的提问（嵌入在对话流中）
   */
  role: 'user' | 'assistant' | 'system' | 'question';
  /** 角色名称 */
  roleName?: string;
  /** 发出此消息的 Agent ID（assistant 消息时有值） */
  agentId?: string;
  /** 发出此消息的 Agent 名称（运行时填充，不存库） */
  agentName?: string;
  /** 消息内容 */
  content: string;
  /** 时间 */
  datetime: string;
  /** 消息状态 */
  status?: 'thinking' | 'retrieving' | 'calling_tool' | 'generating' | 'done' | 'error' | 'success' | string;
  /** 状态名称 */
  statusName?: string;
  /** 状态文本 */
  statusText?: string;
  /** 附加数据 */
  data?: any;
  /** 工具调用记录 */
  toolCalls?: ToolCall[];
  /** RAG 检索结果 */
  ragResults?: RagResult[];
  /** 使用的模型 */
  model?: string;
  /** 是否有错误 */
  error?: boolean;
  /** 是否加载中 */
  loading?: boolean;
  /** 上传的图片 */
  images?: string[];
  /** Token 统计 */
  tokens?: number;
  /** 耗时 */
  duration?: number;
  /** 执行过程（新增） */
  process?: ExecutionProcess;
  /** AskUserQuestion 提问内容（role === 'question' 时有值） */
  question?: UserQuestion;
  /** 是否已回答（role === 'question' 时有效） */
  questionAnswered?: boolean;
  /** 用户的回答内容（已回答后写入） */
  questionAnswer?: string;
}

/**
 * 消息角色枚举
 */
export enum MessageRole {
  /** 用户 */
  USER = 'user',
  /** AI助手 */
  ASSISTANT = 'assistant',
  /** 系统 */
  SYSTEM = 'system',
}

/**
 * 消息角色选项
 */
export const MessageRoleOptions = [
  { code: MessageRole.USER, name: '用户' },
  { code: MessageRole.ASSISTANT, name: 'AI助手' },
  { code: MessageRole.SYSTEM, name: '系统' },
];

/**
 * 消息状态枚举
 */
export enum MessageStatus {
  /** 成功 */
  SUCCESS = 'success',
  /** 错误 */
  ERROR = 'error',
  /** 思考中 */
  THINKING = 'thinking',
}

/**
 * 消息状态选项
 */
export const MessageStatusOptions = [
  { code: MessageStatus.SUCCESS, name: '成功' },
  { code: MessageStatus.ERROR, name: '错误' },
  { code: MessageStatus.THINKING, name: '思考中' },
];

/**
 * 对话状态枚举
 */
export enum ConversationStatus {
  /** 活跃 */
  ACTIVE = 'active',
  /** 归档 */
  ARCHIVED = 'archived',
  /** 已删除 */
  DELETED = 'deleted',
}

/**
 * 对话状态选项
 */
export const ConversationStatusOptions = [
  { code: ConversationStatus.ACTIVE, name: '活跃' },
  { code: ConversationStatus.ARCHIVED, name: '归档' },
  { code: ConversationStatus.DELETED, name: '已删除' },
];

/**
 * 根据code获取枚举名称的工具函数
 */
export const EnumUtils = {
  /**
   * 获取消息角色名称
   */
  getMessageRoleName(code: string): string {
    const option = MessageRoleOptions.find((item) => item.code === code);
    return option?.name || code;
  },

  /**
   * 获取消息状态名称
   */
  getMessageStatusName(code: string): string {
    const option = MessageStatusOptions.find((item) => item.code === code);
    return option?.name || code;
  },

  /**
   * 获取对话状态名称
   */
  getConversationStatusName(code: string): string {
    const option = ConversationStatusOptions.find((item) => item.code === code);
    return option?.name || code;
  },
};

/**
 * 会话信息
 */
export interface ConversationInfo {
  /** 会话ID */
  id: string;
  /** 会话标题 */
  title: string;
  /** 是否可编辑 */
  isEdit?: boolean;
  /** 是否禁用 */
  disabled?: boolean;
  /** 消息数量 */
  messageCount?: number;
  /** 状态 */
  status?: string;
  /** 状态名称 */
  statusName?: string;
  /** 模型ID */
  modelId?: string;
  /** 模型名称 */
  modelName?: string;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
  /** 绑定的 Agent ID */
  agentId?: string;
}

/**
 * MCP 服务器信息（对应后端 McpServerVO）
 */
export interface McpServerInfo {
  /** 服务器 ID */
  id: string;
  /** 显示名称 */
  name: string;
  /** 描述 */
  description?: string;
  /**
   * 作用域：0=GLOBAL（服务端执行），1=PERSONAL（客户端执行）
   */
  scope: 0 | 1;
  /** 能力标签（PERSONAL 类型使用，如 'github'、'notion'） */
  capability?: string;
  /** 连接类型 */
  connectionType: string;
  /** 端点 URL */
  endpointUrl: string;
  /**
   * 认证请求头（键值对）
   * GLOBAL 类型：值脱敏为 "***"；PERSONAL 类型：值为 ""（运行时由浏览器 localStorage 补充）
   */
  authHeaders?: Record<string, string>;
  /** 超时（毫秒） */
  timeoutMs?: number;
  /** 是否启用 */
  enabled: boolean;
  /** 创建人 */
  createdBy?: string;
  /** 创建时间 */
  createdAt?: string;
  /** 工具列表（按需加载） */
  tools?: McpToolInfo[];
  /** 工具数量 */
  toolCount?: number;
}

/**
 * MCP 工具信息
 */
export interface McpToolInfo {
  /** 工具 ID（格式：serverId:toolName） */
  id?: string;
  /** 工具名称 */
  name: string;
  /** 工具描述 */
  description: string;
  /** 是否启用 */
  enabled: boolean;
  /** 所属服务器ID */
  serverId?: string;
  /** 是否是 PERSONAL 工具（客户端执行） */
  personal?: boolean;
  /** 连接类型 */
  connectionType?: string;
}

/**
 * PERSONAL MCP 工具调用事件数据（agent:personal_tool_call）
 */
export interface PersonalToolCallData {
  /** 唯一调用 ID，执行完成后回传给 /api/mcp/client-tool-result */
  callId: string;
  /** MCP 工具名称 */
  toolName: string;
  /** 对应的 PERSONAL MCP 服务器 ID */
  serverId: string;
  /** 工具参数 */
  params: Record<string, unknown>;
}

/**
 * 创建 / 更新 MCP 服务器请求
 */
export interface McpServerRequest {
  name: string;
  description?: string;
  scope: 0 | 1;
  capability?: string;
  connectionType: string;
  endpointUrl: string;
  /**
   * 认证请求头（键值对）
   * GLOBAL 类型：{"Authorization":"Bearer sk-xxx"}
   * PERSONAL 类型：{"Authorization":""} （值留空，运行时由浏览器补充）
   */
  authHeaders?: Record<string, string>;
  extraHeaders?: string;
  timeoutMs?: number;
  readTimeoutMs?: number;
  retryCount?: number;
  enabled?: boolean;
}

/**
 * Agent 事件回调
 */
export interface AgentEventCallbacks {
  onStart?: (event: AgentEvent) => void;
  onIterationStart?: (event: AgentEvent) => void;
  onAskUserQuestion?: (question: UserQuestion) => void;
  onPersonalToolCall?: (data: PersonalToolCallData) => void;
  onThinking?: (event: AgentEvent) => void;
  onThinkingDelta?: (event: AgentEvent) => void;
  onModelSelected?: (event: AgentEvent) => void;
  onRagRetrieve?: (event: AgentEvent) => void;
  onToolCall?: (event: AgentEvent) => void;
  onToolResult?: (event: AgentEvent) => void;
  onMessage?: (event: AgentEvent) => void;
  onStreamComplete?: (event: AgentEvent) => void;
  onIterationEnd?: (event: AgentEvent) => void;
  onInferenceEnd?: (event: AgentEvent) => void;
  onExecuteError?: (event: AgentEvent) => void;
  onFinish?: (event: AgentEvent) => void;
  onStatusUpdate?: (event: AgentEvent) => void;
  onComplete?: (event: AgentEvent) => void;
  onError?: (event: AgentEvent) => void;
}

/**
 * 分页响应结果
 */
export interface PageResult<T> {
  /** 数据列表 */
  records: T[];
  /** 总记录数 */
  total: number;
  /** 当前页码 */
  pageNo: number;
  /** 每页大小 */
  pageSize: number;
  /** 总页数 */
  pages: number;
}

/**
 * 健康检查响应
 */
export interface HealthResponse {
  /** 状态 */
  status: string;
  /** 消息 */
  message: string;
}
