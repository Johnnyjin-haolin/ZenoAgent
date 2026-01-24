/**
 * AI Agent 相关类型定义
 * @author JeecG Team
 * @date 2025-11-30
 */

/**
 * Agent 请求参数
 */
export interface AgentRequest {
  /** 用户输入内容 */
  content: string;
  /** 会话ID（可选，用于上下文关联） */
  conversationId?: string;
  /** 指定使用的模型ID（可选） */
  modelId?: string;
  /** 关联的知识库ID列表 */
  knowledgeIds?: string[];
  /** 启用的工具名称（支持通配符） */
  enabledTools?: string[];
  /** 启用的MCP分组列表（可选，为空则使用所有启用分组） */
  enabledMcpGroups?: string[];
  /** 执行模式：AUTO-自动 / MANUAL-手动 */
  mode?: 'AUTO' | 'MANUAL';
  /** 自定义上下文参数 */
  context?: Record<string, any>;
}

/**
 * Agent 事件类型
 */
export type AgentEventType =
  | 'agent:start'           // 任务开始
  | 'agent:thinking'        // AI 思考中
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
  | 'agent:stream_complete' // 流式输出完成
  | 'agent:complete'        // 任务完成
  | 'agent:error';          // 错误发生

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
 * 执行过程
 */
export interface ExecutionProcess {
  /** 是否折叠 */
  collapsed: boolean;
  /** 步骤列表 */
  steps: ProcessStep[];
  /** 总耗时 */
  totalDuration?: number;
  /** 完成步骤数 */
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
  /** 角色 */
  role: 'user' | 'assistant' | 'system';
  /** 角色名称 */
  roleName?: string;
  /** 消息内容 */
  content: string;
  /** 时间 */
  datetime: string;
  /** 消息状态 */
  status?: 'thinking' | 'retrieving' | 'calling_tool' | 'generating' | 'done' | 'error' | 'success';
  /** 状态名称 */
  statusName?: string;
  /** 状态文本 */
  statusText?: string;
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
}

/**
 * MCP分组信息
 */
export interface McpGroupInfo {
  /** 分组ID */
  id: string;
  /** 分组名称 */
  name: string;
  /** 分组描述 */
  description?: string;
  /** 是否启用 */
  enabled: boolean;
  /** 工具数量 */
  toolCount: number;
  /** 所属服务器ID */
  serverId?: string;
  /** 连接类型 */
  connectionType?: string;
}

/**
 * MCP工具信息
 */
export interface McpToolInfo {
  /** 工具名称 */
  name: string;
  /** 工具描述 */
  description: string;
  /** 工具分组 */
  group: string;
  /** 是否启用 */
  enabled: boolean;
  /** 工具版本 */
  version?: string;
  /** 所属服务器ID */
  serverId?: string;
  /** 连接类型 */
  connectionType?: string;
}

/**
 * Agent 事件回调
 */
export interface AgentEventCallbacks {
  onStart?: (event: AgentEvent) => void;
  onThinking?: (event: AgentEvent) => void;
  onModelSelected?: (event: AgentEvent) => void;
  onRagRetrieve?: (event: AgentEvent) => void;
  onToolCall?: (event: AgentEvent) => void;
  onToolResult?: (event: AgentEvent) => void;
  onMessage?: (event: AgentEvent) => void;
  onStreamComplete?: (event: AgentEvent) => void;
  onComplete?: (event: AgentEvent) => void;
  onError?: (event: AgentEvent) => void;
}

