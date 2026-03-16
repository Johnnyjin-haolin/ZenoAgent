/**
 * Agent 聊天逻辑 Hook
 * @date 2025-11-30
 */

import { ref, Ref, computed, reactive, nextTick } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { SSEClientTransport } from '@modelcontextprotocol/sdk/client/sse.js';
import logger from '@/utils/logger';
import {
  executeAgent,
  getConversationMessages,
  confirmToolExecution,
  stopAgent,
  submitAnswer,
  submitClientToolResult,
  prefetchPersonalMcpTools,
} from '../agent.api';
import {
  canExecutePersonalMcp,
  buildPersonalMcpHeaders,
  getMissingSecretHeaders,
} from '@/utils/mcpSecretStore';
import type {
  AgentMessage,
  AgentRequest,
  AgentEvent,
  ToolCall,
  RagResult,
  ProcessStep,
  ProcessStepType,
  ProcessStepStatus,
  PlanInfo,
  ProcessSubStep,
  UserQuestion,
  ExecutionProcess,
  ReActIteration,
  PersonalToolCallData,
  McpServerInfo,
} from '../agent.types';

export interface UseAgentChatOptions {
  /** 会话ID */
  conversationId?: Ref<string>;
  /** 默认模型ID */
  defaultModelId?: string;
  /** 默认知识库IDs */
  defaultKnowledgeIds?: string[];
  /**
   * 返回本次对话需要参与 prefetch 的 PERSONAL MCP 服务器列表
   * 在 sendMessage 前会并发调用各服务器 tools/list，获取真实工具 schema
   */
  getPersonalMcpServers?: () => McpServerInfo[];
  /** 根据 agentId 查询 Agent 名称（运行时填充 agentName 用） */
  getAgentName?: (agentId: string) => string | undefined;
  /** 获取 PERSONAL MCP 服务器信息（用于客户端执行，SSE 下发时路由） */
  getPersonalMcpServer?: (serverId: string) => McpServerInfo | undefined;
}

/**
 * PERSONAL MCP 密钥补充请求（行内提示用）
 */
export interface PendingSecretRequest {
  /** 唯一 ID（同 callId，方便配对） */
  callId: string;
  /** MCP 服务器 ID */
  serverId: string;
  /** MCP 服务器名称（展示用） */
  serverName: string;
  /** 缺少密钥的 Header 名列表 */
  missingHeaders: string[];
  /** 工具名称 */
  toolName: string;
  /** 待执行的工具参数（密钥补充后继续使用） */
  params: Record<string, unknown>;
}

/**
 * 调用 PERSONAL MCP endpoint（通过 SDK，遵循标准 MCP 协议）
 *
 * 流程：connect（initialize/initialized 握手）→ tools/call → close
 */
async function callPersonalMcpEndpoint(
  server: McpServerInfo,
  toolName: string,
  params: Record<string, unknown>,
  extraHeaders: Record<string, string> = {}
): Promise<string> {
  const authHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json, text/event-stream',
    ...buildPersonalMcpHeaders(server.id, server.authHeaders),
    ...extraHeaders,
  };

  const url = new URL(server.endpointUrl);
  const client = new Client({ name: 'zeno-agent', version: '1.0.0' });

  let transport: StreamableHTTPClientTransport | SSEClientTransport;
  if (server.connectionType === 'sse') {
    transport = new SSEClientTransport(url, { requestInit: { headers: authHeaders } });
  } else {
    transport = new StreamableHTTPClientTransport(url, { requestInit: { headers: authHeaders } });
  }

  try {
    await client.connect(transport);
    const result = await client.callTool({ name: toolName, arguments: params });

    const content = result?.content;
    if (Array.isArray(content)) {
      return content
        .map((c) => {
          if (typeof c === 'object' && c !== null && 'text' in c) return String(c.text);
          return JSON.stringify(c);
        })
        .join('\n');
    }
    return JSON.stringify(result);
  } finally {
    client.close().catch(() => {});
  }
}

/**
 * Agent 聊天逻辑封装
 */
export function useAgentChat(options: UseAgentChatOptions = {}) {
  const { t } = useI18n();
  const {
    conversationId,
    defaultModelId,
    defaultKnowledgeIds = [],
    getPersonalMcpServers,
    getAgentName,
    getPersonalMcpServer,
  } = options;

  // 消息列表
  const messages = ref<AgentMessage[]>([]);
  
  // 加载状态
  const loading = ref(false);
  
  // 当前状态文本
  const currentStatus = ref('');
  
  // 当前请求的 AbortController
  let currentController: AbortController | null = null;
  
  // 当前请求 ID（用于停止功能）
  let currentRequestId: string | null = null;

  /**
   * 工具确认队列
   */
  const pendingToolConfirmations = ref<Array<{
    requestId: string;
    toolExecutionId: string;
    toolName: string;
    params: Record<string, any>;
  }>>([]);

  /**
   * 当前待回答的用户提问（来自 system_ask_user_question 工具）
   * null 表示当前没有待回答的问题
   */
  const pendingQuestion = ref<UserQuestion | null>(null);

  /**
   * 当前待补充密钥的 PERSONAL MCP 请求
   * null 表示无需补充密钥
   */
  const pendingSecretRequest = ref<PendingSecretRequest | null>(null);

  /**
   * 创建执行步骤
   */
  const createStep = (
    type: ProcessStepType,
    name: string,
    status: ProcessStepStatus = 'waiting',
    metadata?: any
  ): ProcessStep => {
    return {
      id: `step-${type}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      type,
      name,
      status,
      startTime: status === 'running' ? Date.now() : undefined,
      expanded: type === 'thinking',
      metadata: metadata || {},
    };
  };

  /**
   * 查找步骤
   */
  const findStep = (steps: ProcessStep[], type: ProcessStepType, toolName?: string): ProcessStep | undefined => {
    if (type === 'tool_call' && toolName) {
      return [...steps].reverse().find((s) =>
        s.type === type && s.metadata?.toolName === toolName && ['waiting', 'running'].includes(s.status)
      );
    }
    return [...steps].reverse().find((s) => s.type === type);
  };

  /**
   * 完成步骤
   */
  const finishStep = (
    steps: ProcessStep[],
    type: ProcessStepType,
    status: ProcessStepStatus = 'success',
    metadata?: any,
    toolName?: string
  ) => {
    const step = findStep(steps, type, toolName);
    if (step) {
      step.status = status;
      step.endTime = Date.now();
      step.duration = step.startTime ? step.endTime - step.startTime : undefined;
      if (metadata) {
        step.metadata = { ...step.metadata, ...metadata };
      }
    }
  };

  /**
   * 更新工具步骤状态
   */
  const updateToolStepStatus = (
    steps: ProcessStep[],
    toolName: string,
    status: ProcessStepStatus,
    metadata?: any
  ) => {
    const step = findStep(steps, 'tool_call', toolName);
    if (step) {
      step.status = status;
      if (status === 'running') {
        step.startTime = step.startTime || Date.now();
      }
      if (metadata) {
        step.metadata = { ...step.metadata, ...metadata };
      }
    }
  };

  /**
   * 解析 thinking 消息
   */
  const parseThinkingMessage = (event: AgentEvent) => {
    const msg = event.message || '';
    const data = event.data || {};

    if (data.steps && Array.isArray(data.steps)) {
      return {
        type: 'plan' as const,
        planInfo: {
          planId: data.planId,
          taskType: data.taskType,
          steps: data.steps,
          variables: data.variables,
        },
        message: msg,
      };
    }

    const stepMatch = msg.match(/步骤\s*(\d+)\/(\d+):\s*(.+)/);
    if (stepMatch) {
      return {
        type: 'step' as const,
        stepProgress: {
          current: parseInt(stepMatch[1]),
          total: parseInt(stepMatch[2]),
          description: stepMatch[3],
        },
        message: msg,
      };
    }

    return {
      type: 'thinking' as const,
      message: msg,
    };
  };

  /**
   * 执行 PERSONAL MCP 工具调用（浏览器端）
   * 如果密钥缺失，将设置 pendingSecretRequest 触发行内密钥补充卡片
   */
  const executePersonalToolCall = async (data: PersonalToolCallData) => {
    const server = getPersonalMcpServer?.(data.serverId);
    if (!server) {
      logger.error('[PERSONAL MCP] 找不到服务器信息，serverId:', data.serverId);
      await submitClientToolResult(data.callId, undefined, `找不到 PERSONAL MCP 服务器: ${data.serverId}`);
      return;
    }

    // 检查是否需要密钥且已全部配置
    if (!canExecutePersonalMcp(server.id, server.authHeaders)) {
      const missingHeaders = getMissingSecretHeaders(server.id, server.authHeaders);
      logger.debug('[PERSONAL MCP] 密钥缺失，等待用户补充:', server.name, '缺少:', missingHeaders);
      pendingSecretRequest.value = {
        callId: data.callId,
        serverId: server.id,
        serverName: server.name,
        missingHeaders,
        toolName: data.toolName,
        params: data.params,
      };
      return; // 等待用户通过行内卡片补充密钥后调用 resolveSecretRequest
    }

    await doCallPersonalMcp(server, data);
  };

  /**
   * 实际执行 PERSONAL MCP fetch，并回传结果
   */
  const doCallPersonalMcp = async (server: McpServerInfo, data: PersonalToolCallData) => {
    try {
      logger.debug('[PERSONAL MCP] 执行工具调用:', data.toolName, data.params);
      const result = await callPersonalMcpEndpoint(server, data.toolName, data.params);
      logger.debug('[PERSONAL MCP] 工具调用成功:', result.substring(0, 100));
      await submitClientToolResult(data.callId, result, undefined);
    } catch (err: any) {
      const errMsg = err?.message || String(err);
      logger.error('[PERSONAL MCP] 工具调用失败:', errMsg);
      await submitClientToolResult(data.callId, undefined, errMsg);
    }
  };

  /**
   * 用户补充密钥后继续执行（由 McpSecretInlineCard 组件触发）
   */
  const resolveSecretRequest = async (secret: string) => {
    const req = pendingSecretRequest.value;
    if (!req) return;

    const server = getPersonalMcpServer?.(req.serverId);
    if (!server) {
      await submitClientToolResult(req.callId, undefined, `服务器不存在: ${req.serverId}`);
      pendingSecretRequest.value = null;
      return;
    }

    pendingSecretRequest.value = null;

    // 用用户补充的临时密钥（不保存到 localStorage，仅本次使用）
    // secret 为用户输入的内容，若缺少多个 header 则用同一个值临时填充
    const extraHeaders: Record<string, string> = {};
    for (const headerName of req.missingHeaders) {
      extraHeaders[headerName] = secret.startsWith('Bearer ') ? secret : `Bearer ${secret}`;
    }

    try {
      const result = await callPersonalMcpEndpoint(server, req.toolName, req.params, extraHeaders);
      await submitClientToolResult(req.callId, result, undefined);
    } catch (err: any) {
      await submitClientToolResult(req.callId, undefined, err?.message || String(err));
    }
  };

  /**
   * 用户跳过密钥补充（拒绝执行该工具）
   */
  const skipSecretRequest = async () => {
    const req = pendingSecretRequest.value;
    if (!req) return;
    pendingSecretRequest.value = null;
    await submitClientToolResult(req.callId, undefined, '用户跳过了密钥配置，工具未执行');
  };

  /**
   * 发送消息
   */
  const sendMessage = async (
    content: string,
    options: {
      agentId?: string;
      modelId?: string;
      knowledgeIds?: string[];
      mcpServers?: import('../agent.types').McpServerSelection[];
      mode?: 'AUTO' | 'MANUAL';
      images?: string[];
    } = {}
  ) => {
    if (loading.value) {
      message.warning(t('agent.chat.waitCurrent'));
      return;
    }

    if (!content.trim()) {
      message.warning(t('agent.chat.inputMsg'));
      return;
    }

    loading.value = true;
    currentStatus.value = t('agent.chat.preparing');

    const updateConversationId = (event: AgentEvent) => {
      const newConversationId = event.conversationId || (event.data && event.data.conversationId);
      if (newConversationId && conversationId && conversationId.value !== newConversationId) {
        conversationId.value = newConversationId;
      }
    };

    // 添加用户消息
    const userMessage: AgentMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: content.trim(),
      datetime: new Date().toLocaleString(),
      images: options.images,
    };
    messages.value.push(userMessage);

    // 添加助手消息占位（使用 reactive 确保响应式）
    const assistantMessage = reactive<AgentMessage>({
      id: `assistant-${Date.now()}`,
      role: 'assistant',
      content: '',
      datetime: new Date().toLocaleString(),
      status: 'thinking',
      statusText: t('agent.chat.preparing'),
      loading: true,
      toolCalls: [],
      ragResults: [],
      agentId: options.agentId,
      agentName: options.agentId ? getAgentName?.(options.agentId) : undefined,
      process: {
        iterations: [],
        completedCount: 0,
        streamingStarted: false,
      },
    });
    messages.value.push(assistantMessage);

    // 当前迭代对象引用
    let currentIteration: any = null;

    // Prefetch PERSONAL MCP 工具 schema（并发，失败的服务器静默跳过）
    let personalMcpTools: import('../agent.types').PersonalMcpToolSchema[] = [];
    const personalServers = getPersonalMcpServers?.() ?? [];
    if (personalServers.length > 0) {
      const results = await Promise.allSettled(
        personalServers.map((srv) =>
          prefetchPersonalMcpTools(srv, buildPersonalMcpHeaders(srv.id, srv.authHeaders))
        )
      );
      for (const r of results) {
        if (r.status === 'fulfilled') {
          personalMcpTools = personalMcpTools.concat(r.value);
        }
      }
      logger.debug(`[useAgentChat] prefetch PERSONAL MCP 工具完成，共 ${personalMcpTools.length} 个`);
    }

    // 构建请求（合并运行时传入的 MCP 配置）
    const request: AgentRequest = {
      content: content.trim(),
      conversationId: conversationId?.value,
      agentId: options.agentId,
      modelId: options.modelId || defaultModelId,
      knowledgeIds: options.knowledgeIds || defaultKnowledgeIds,
      mcpServers: options.mcpServers,
      mode: options.mode || 'AUTO',
      personalMcpTools: personalMcpTools.length > 0 ? personalMcpTools : undefined,
    };

    try {
      currentController = await executeAgent(request, {
        onAskUserQuestion: (question) => {
          logger.debug('[useAgentChat] 收到用户提问:', question);
          pendingQuestion.value = question;

          if (assistantMessage.process?.iterations) {
            const allIterations = assistantMessage.process.iterations as any[];
            for (let i = allIterations.length - 1; i >= 0; i--) {
              const steps: any[] = allIterations[i].steps || [];
              const askStep = [...steps].reverse().find(
                (s: any) => s.type === 'tool_call' && s.metadata?.isAskUser && s.status === 'waiting'
              );
              if (askStep) {
                askStep.metadata.question = question;
                logger.debug('[useAgentChat] 已更新步骤 metadata.question，真实 questionId:', question.questionId);
                break;
              }
            }
          }
        },

        onPersonalToolCall: async (data) => {
          logger.debug('[useAgentChat] 收到 PERSONAL MCP 工具调用:', data);
          await executePersonalToolCall(data);
        },

        onStart: (event) => {
          logger.debug('任务开始:', event);
          updateConversationId(event);
          currentRequestId = event.requestId || null;
          assistantMessage.status = 'thinking';
          assistantMessage.statusText = t('agent.chat.processing');
          currentStatus.value = t('agent.chat.started');
        },

        onIterationStart: (event) => {
          logger.debug('迭代开始:', event);
          updateConversationId(event);

          const iterationNumber = event.data?.iterationNumber ?? (assistantMessage.process!.iterations.length + 1);
          const newIteration: any = reactive({
            iterationNumber,
            steps: [],
            status: 'running',
            startTime: Date.now(),
            collapsed: false,
          });

          assistantMessage.process!.iterations.push(newIteration);
          currentIteration = newIteration;

          logger.debug(`🔁 创建第 ${iterationNumber} 轮迭代`);
        },

        onStatusUpdate: (event) => {
          logger.debug('状态更新:', event);
          updateConversationId(event);
          assistantMessage.status = event.event;
          assistantMessage.data = event.data;
          assistantMessage.statusText = undefined;
          
          let statusText = '';
          if (event.event === 'agent:status:analyzing') {
            statusText = t('agent.status.analyzing');
          } else if (event.event === 'agent:status:thinking_process') {
            statusText = t('agent.status.thinking_process');
          } else if (event.event === 'agent:status:planning') {
            statusText = t('agent.status.planning');
          } else if (event.event === 'agent:status:rag_querying') {
            statusText = t('agent.status.rag_querying');
          } else if (event.event === 'agent:status:tool_executing_single') {
            statusText = t('agent.status.tool_executing_single', { 
              toolName: event.data?.toolName || 'Tool' 
            });
          } else if (event.event === 'agent:status:tool_executing_batch') {
            statusText = t('agent.status.tool_executing_batch', { 
              count: event.data?.count || 0 
            });
          } else if (event.event === 'agent:status:retrying') {
            statusText = t('agent.status.retrying', {
              attempt: event.data?.attempt || 1
            });
          }
          
          if (statusText) {
            currentStatus.value = statusText;
          }
        },

        onThinking: (event) => {
          logger.debug('AI 思考中:', event);
          updateConversationId(event);
          assistantMessage.status = 'thinking';
          if (event.message) {
            assistantMessage.statusText = event.message;
            currentStatus.value = event.message;
          } else if (!assistantMessage.statusText) {
            assistantMessage.statusText = t('agent.chat.thinking');
            currentStatus.value = t('agent.chat.thinking');
          }

          if (!currentIteration) return;

          let thinkingStep = currentIteration.steps.find((s: any) => s.type === 'thinking');
          if (!thinkingStep) {
            thinkingStep = createStep('thinking', t('agent.chat.thinkingProcess'), 'running');
            thinkingStep.subSteps = [];
            currentIteration.steps.push(thinkingStep);
          }

          if (event.message && event.message.includes('思考完成')) {
            thinkingStep.expanded = false;
          }
        },

        onThinkingDelta: (event) => {
          logger.debug('AI 思考片段:', event.content);
          updateConversationId(event);
          assistantMessage.status = 'thinking';

          if (!currentIteration) return;

          let thinkingStep = currentIteration.steps.find((s: any) => s.type === 'thinking');
          if (!thinkingStep) {
            thinkingStep = createStep('thinking', t('agent.chat.thinkingProcess'), 'running');
            thinkingStep.subSteps = [];
            currentIteration.steps.push(thinkingStep);
          }

          if (!thinkingStep.subSteps) {
            thinkingStep.subSteps = [];
          }
          const lastStep = thinkingStep.subSteps[thinkingStep.subSteps.length - 1];
          if (lastStep) {
            lastStep.message = (lastStep.message || '') + (event.content || '');
          } else {
            thinkingStep.subSteps.push({
              id: `substep-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
              message: event.content || '',
              timestamp: Date.now(),
            });
          }
        },

        onModelSelected: (event) => {
          logger.debug('模型已选择:', event);
          updateConversationId(event);
          if (event.data) {
            assistantMessage.model = event.data.name || event.data.id;
          }
          currentStatus.value = t('agent.chat.usingModel', { model: event.data?.name || '' });
        },

        onRagRetrieve: (event) => {
          logger.debug('RAG 检索:', event);
          updateConversationId(event);
          assistantMessage.status = 'retrieving';
          assistantMessage.statusText = t('agent.chat.retrievingKb');
          currentStatus.value = t('agent.chat.retrievingKbProcess');

          if (!currentIteration) return;

          const thinkingStep = currentIteration.steps.find((s: any) => s.type === 'thinking');
          if (thinkingStep && thinkingStep.status === 'running') {
            thinkingStep.status = 'success';
            thinkingStep.endTime = Date.now();
            thinkingStep.duration = thinkingStep.endTime - (thinkingStep.startTime || 0);
          }

          let ragResults: RagResult[] = [];
          let retrieveCount = 0;
          let avgScore = 0;

          if (event.data) {
            if (Array.isArray(event.data)) {
              ragResults = event.data;
              retrieveCount = event.data.length;
            } else if (event.data.knowledgeIds) {
              retrieveCount = event.data.resultCount || 0;
              avgScore = event.data.avgScore || 0;
              ragResults.push({
                content: t('agent.chat.retrievedInfo', { count: retrieveCount, score: avgScore }),
                score: avgScore,
                source: event.data.query,
              });
            }
            
            assistantMessage.ragResults = ragResults;
          }

          const step = createStep('rag_retrieve', t('agent.chat.retrieveKbStep'), 'running', {
            retrieveCount,
            avgScore,
            ragResults,
          });
          currentIteration.steps.push(step);
        },

        onToolCall: (event) => {
          logger.debug('工具调用:', event);
          updateConversationId(event);

          const toolName: string = event.data?.toolName || '';
          const isAskUser = toolName === 'system_ask_user_question';

          if (isAskUser) {
            let rawParams = event.data?.params;
            let parsedParams: Record<string, any> = {};
            if (typeof rawParams === 'string') {
              try { parsedParams = JSON.parse(rawParams); } catch { /* 解析失败忽略 */ }
            } else if (rawParams && typeof rawParams === 'object') {
              parsedParams = rawParams as Record<string, any>;
            }

            const questionText: string = parsedParams.question || '';
            const tempId = `pending-${Date.now()}`;

            if (!currentIteration) {
              const newIteration = reactive({
                iterationNumber: assistantMessage.process!.iterations.length + 1,
                steps: [],
                status: 'running',
                startTime: Date.now(),
                collapsed: false,
              });
              assistantMessage.process!.iterations.push(newIteration);
              currentIteration = newIteration;
            }

            const tempQuestion: UserQuestion = {
              questionId: tempId,
              question: questionText,
              questionType: (parsedParams.questionType as any) || 'INPUT',
              options: parsedParams.options,
              previewContent: parsedParams.previewContent,
            };
            pendingQuestion.value = tempQuestion;

            const askStep = createStep(
              'tool_call',
              t('agent.chat.askingUser') || '等待用户回答',
              'waiting',
              { toolName, toolParams: parsedParams, isAskUser: true, question: tempQuestion }
            );
            askStep.expanded = true;
            currentIteration.steps.push(askStep);

            logger.debug('[useAgentChat] 插入 ask_user 步骤，临时 questionId:', tempId);
            return;
          }

          const requiresConfirmation = Boolean(event.data?.requiresConfirmation);
          assistantMessage.status = 'calling_tool';
          assistantMessage.statusText = requiresConfirmation
            ? t('agent.chat.waitingConfirm', { tool: toolName })
            : t('agent.chat.callingToolStep', { tool: toolName });
          currentStatus.value = assistantMessage.statusText || '';

          if (!currentIteration) {
            const newIteration = reactive({
              iterationNumber: assistantMessage.process!.iterations.length + 1,
              steps: [],
              status: 'running',
              startTime: Date.now(),
              collapsed: false,
            });
            assistantMessage.process!.iterations.push(newIteration);
            currentIteration = newIteration;
            logger.debug(`🔁 [onToolCall] 自动创建迭代 #${newIteration.iterationNumber}`);
          }

          const thinkingStep = currentIteration.steps.find((s: any) => s.type === 'thinking');
          if (thinkingStep && thinkingStep.status === 'running') {
            thinkingStep.status = 'success';
            thinkingStep.endTime = Date.now();
            thinkingStep.duration = thinkingStep.endTime - (thinkingStep.startTime || 0);
          }

          if (event.data && toolName) {
            const toolCall: ToolCall = {
              name: toolName,
              params: event.data.params || {},
              status: 'pending',
            };
            assistantMessage.toolCalls?.push(toolCall);

            const stepStatus: ProcessStepStatus = requiresConfirmation ? 'waiting' : 'running';
            const step = createStep('tool_call', t('agent.chat.callingToolStep', { tool: toolName }), stepStatus, {
              toolName,
              toolParams: event.data.params || {},
              requiresConfirmation,
            });
            currentIteration.steps.push(step);

            if (requiresConfirmation && event.data.toolExecutionId) {
              pendingToolConfirmations.value.push({
                requestId: event.requestId,
                toolExecutionId: event.data.toolExecutionId,
                toolName,
                params: event.data.params || {},
              });
            }
          }
        },

        onToolResult: (event) => {
          logger.debug('工具结果:', event);
          updateConversationId(event);
          currentStatus.value = t('agent.chat.toolExecDone');

          if (!event.data) return;

          const { toolName, result, error, duration } = event.data;

          if (assistantMessage.toolCalls && assistantMessage.toolCalls.length > 0) {
            const lastTool = assistantMessage.toolCalls[assistantMessage.toolCalls.length - 1];
            if (lastTool && lastTool.name === toolName) {
              lastTool.result = result;
              lastTool.status = error ? 'error' : 'success';
              lastTool.duration = duration;
              if (error) {
                lastTool.error = error;
              }
            }
          }

          if (!currentIteration) return;

          const steps: any[] = currentIteration.steps;
          let toolStep: any = null;
          for (let i = steps.length - 1; i >= 0; i--) {
            if (steps[i].type === 'tool_call' && steps[i].metadata?.toolName === toolName) {
              toolStep = steps[i];
              break;
            }
          }

          if (toolStep) {
            toolStep.status = error ? 'error' : 'success';
            toolStep.endTime = Date.now();
            toolStep.duration = duration ?? (toolStep.startTime ? toolStep.endTime - toolStep.startTime : undefined);
            if (toolStep.metadata) {
              toolStep.metadata.toolResult = result;
              toolStep.metadata.toolError = error;
              toolStep.metadata.toolDuration = duration;
            }
          }
        },

        onMessage: (event) => {
          logger.debug('[useAgentChat] 收到消息片段:', event.content);
          updateConversationId(event);

          assistantMessage.status = 'generating';
          assistantMessage.statusText = t('agent.chat.generating');
          assistantMessage.loading = true;
          assistantMessage.content += event.content || '';
          currentStatus.value = t('agent.chat.generating');
        },

        onIterationEnd: (event) => {
          logger.debug('迭代结束:', event);
          updateConversationId(event);

          if (!currentIteration) return;

          currentIteration.steps.forEach((step: any) => {
            if (step.status === 'running') {
              step.status = 'success';
              step.endTime = Date.now();
              step.duration = step.startTime ? step.endTime - step.startTime : undefined;
            }
          });

          currentIteration.status = 'completed';
          currentIteration.endTime = Date.now();
          currentIteration.totalDuration = currentIteration.endTime - currentIteration.startTime;
          currentIteration.collapsed = true;

          logger.debug(`🔁 完成第 ${currentIteration.iterationNumber} 轮迭代（自动折叠）`);
          currentIteration = null;
        },

        onStreamComplete: (event) => {
          logger.debug('[useAgentChat] 流式输出完成');
          updateConversationId(event);
          
          assistantMessage.status = 'done';
          assistantMessage.loading = false;
          currentStatus.value = '';
        },

        onComplete: (event) => {
          logger.debug('任务完成:', event);
          updateConversationId(event);
          
          if (assistantMessage.process && assistantMessage.process.iterations) {
            assistantMessage.process.iterations.forEach((iter: any) => {
              if (iter.status === 'running') {
                logger.warn(`⚠️ 发现未完成的迭代 ${iter.iterationNumber}，强制标记为completed`);
                
                iter.steps?.forEach((step: any) => {
                  if (step.status === 'running') {
                    step.status = 'success';
                    step.endTime = Date.now();
                    step.duration = step.startTime ? step.endTime - step.startTime : undefined;
                  }
                });
                
                iter.status = 'completed';
                iter.endTime = Date.now();
                iter.totalDuration = iter.endTime - iter.startTime;
                iter.collapsed = true;
              }
            });
          }
          
          assistantMessage.status = 'done';
          assistantMessage.statusText = t('agent.chat.finish');
          assistantMessage.loading = false;
          assistantMessage.tokens = event.totalTokens;
          assistantMessage.duration = event.duration;

          assistantMessage.process!.totalDuration = event.duration;
          assistantMessage.process!.completedCount = assistantMessage.process!.iterations.filter(
            (iter: any) => iter.status === 'completed'
          ).length;

          loading.value = false;
          currentStatus.value = t('agent.chat.finish');
          currentController = null;

          if (event.data?.conversationId && conversationId) {
            conversationId.value = event.data.conversationId;
          }

          message.success(t('agent.chat.finish'));
        },

        onInferenceEnd: (event) => {
          logger.debug('推理结束:', event);
          updateConversationId(event);
          assistantMessage.statusText = t('agent.chat.inferenceEnd');
          currentStatus.value = t('agent.chat.continueNext');
        },

        onExecuteError: (event) => {
          logger.error('执行出错:', event);
          updateConversationId(event);
          loading.value = false;
          assistantMessage.status = 'error';
          assistantMessage.statusText = t('agent.chat.execError', { msg: event.error });
          currentStatus.value = t('agent.chat.processFailed');
          
          message.error(event.message || t('agent.chat.processFailed'));
        },

        onError: (event) => {
          logger.error('发生错误:', event);
          updateConversationId(event);
          assistantMessage.status = 'error';
          assistantMessage.statusText = event.error || t('agent.chat.error');
          assistantMessage.loading = false;
          assistantMessage.error = true;
          assistantMessage.content = event.message || t('agent.chat.processFailed');
          
          if (currentIteration && currentIteration.steps.length > 0) {
            currentIteration.steps.forEach((step: any) => {
              if (step.status === 'running') {
                step.status = 'error';
                step.endTime = Date.now();
                step.duration = step.startTime ? step.endTime - step.startTime : undefined;
                step.metadata = { ...step.metadata, errorMessage: event.message };
              }
            });
            
            currentIteration.status = 'error';
            currentIteration.endTime = Date.now();
            currentIteration.totalDuration = currentIteration.endTime - currentIteration.startTime;
            currentIteration.shouldContinue = false;
            currentIteration.terminationReason = 'EXCEPTION';
            currentIteration.terminationMessage = t('agent.chat.execError', { msg: event.message || t('agent.chat.error') });
            currentIteration.collapsed = true;
          }

          loading.value = false;
          currentStatus.value = t('agent.chat.processFailed');
          currentController = null;

          message.error(event.message || t('agent.chat.sendFailed'));
        },
      });
    } catch (error: any) {
      logger.error('发送消息失败:', error);
      assistantMessage.status = 'error';
      assistantMessage.loading = false;
      assistantMessage.error = true;
      assistantMessage.content = t('agent.chat.sendFailed');
      
      loading.value = false;
      currentStatus.value = t('agent.chat.sendFailed');
      
      message.error(t('agent.chat.sendFailed'));
    }
  };

  /**
   * 停止生成
   */
  const stopGeneration = async () => {
    if (currentController) {
      try {
        if (currentRequestId) {
          logger.debug('调用后端停止接口:', currentRequestId);
          const success = await stopAgent(currentRequestId);
          logger.debug('后端停止结果:', success);
        }
        
        await new Promise(resolve => setTimeout(resolve, 100));
        
        currentController.abort();
      } catch (error) {
        logger.error('停止失败:', error);
      } finally {
        currentController = null;
        currentRequestId = null;
        loading.value = false;
        currentStatus.value = t('agent.chat.stopGen');
        
        const lastMessage = messages.value[messages.value.length - 1];
        if (lastMessage && lastMessage.role === 'assistant') {
          lastMessage.status = 'done';
          lastMessage.loading = false;
          lastMessage.statusText = t('agent.chat.stopGen');
        }
        
        message.info(t('agent.chat.stopGen'));
      }
    }
  };

  /**
   * 提交用户对 Agent 提问的回答
   */
  const resolveQuestion = async (questionId: string, answer: string) => {
    const questionMsg = messages.value.find(
      (m) => m.role === 'question' && m.question?.questionId === questionId
    ) as any;
    if (questionMsg) {
      questionMsg.questionAnswered = true;
      questionMsg.questionAnswer = answer;
    }

    for (const msg of messages.value) {
      if (msg.role !== 'assistant' || !msg.process?.iterations) continue;
      for (const iter of msg.process.iterations as any[]) {
        if (!iter.steps) continue;
        const askStep = [...iter.steps].reverse().find(
          (s: any) => s.type === 'tool_call' && s.metadata?.isAskUser && s.status === 'waiting'
        );
        if (askStep) {
          askStep.status = 'success';
          askStep.endTime = Date.now();
          askStep.duration = askStep.startTime ? askStep.endTime - askStep.startTime : 0;
          askStep.metadata = { ...askStep.metadata, toolResult: answer };
          break;
        }
      }
    }

    pendingQuestion.value = null;

    const success = await submitAnswer(questionId, answer);
    if (!success) {
      message.error(t('agent.chat.submitAnswerFailed'));
    }
  };

  /**
   * 确认/拒绝当前工具执行
   */
  const resolvePendingTool = async (approve: boolean) => {
    const current = pendingToolConfirmations.value[0];
    if (!current) return;

    const success = await confirmToolExecution(current.toolExecutionId, approve, current.requestId);
    if (!success) {
      message.error(t('agent.chat.confirmFailed'));
      return;
    }

    const lastMessage = messages.value[messages.value.length - 1];
    if (lastMessage && lastMessage.role === 'assistant' && lastMessage.process) {
      const iterations = lastMessage.process.iterations;
      if (iterations && iterations.length > 0) {
        const currentIter = iterations[iterations.length - 1];
        if (currentIter && currentIter.steps) {
          if (approve) {
            updateToolStepStatus(currentIter.steps, current.toolName, 'running');
          } else {
            updateToolStepStatus(currentIter.steps, current.toolName, 'error', {
              toolError: t('agent.chat.userRejected'),
            });
          }
        }
      }
    }

    pendingToolConfirmations.value.shift();
  };

  /**
   * 清空消息
   */
  const clearMessages = () => {
    messages.value = [];
    currentStatus.value = '';
  };

  /**
   * 加载历史消息
   */
  const loadMessages = async (conversationId: string) => {
    if (!conversationId) {
      messages.value = [];
      return;
    }

    loading.value = true;
    try {
      const rawMessages = await getConversationMessages(conversationId);
      messages.value = rawMessages.map(convertToAgentMessage);
      logger.debug(`已加载 ${messages.value.length} 条历史消息`);
    } catch (error) {
      logger.error('加载历史消息失败:', error);
      message.error(t('agent.chat.loadHistoryFailed'));
      messages.value = [];
    } finally {
      loading.value = false;
    }
  };

  /**
   * 转换后端消息格式为前端格式
   */
  const convertToAgentMessage = (raw: any): AgentMessage => {
    const agentId: string | undefined = raw.agentId || undefined;
    return {
      id: raw.id || raw.messageId || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      role: mapRole(raw.role),
      content: raw.content || '',
      datetime: raw.createTime || new Date().toISOString(),
      status: 'done',
      model: raw.modelId,
      agentId,
      agentName: agentId ? getAgentName?.(agentId) : undefined,
      tokens: raw.tokens,
      duration: raw.duration,
      toolCalls: extractToolCalls(raw.metadata),
      ragResults: extractRagResults(raw.metadata),
      process: extractExecutionProcess(raw.metadata),
    };
  };

  /**
   * 从 metadata.executionProcess 还原执行过程
   */
  const extractExecutionProcess = (metadata: any): ExecutionProcess | undefined => {
    if (!metadata || typeof metadata !== 'object') return undefined;

    const ep = metadata.executionProcess;
    if (!ep || !Array.isArray(ep.iterations) || ep.iterations.length === 0) return undefined;

    const iterations = ep.iterations.map((iter: any, idx: number) => {
      const steps: ProcessStep[] = [];
      const rawSteps: any[] = Array.isArray(iter.steps) ? iter.steps : [];

      let thinkingContent = '';
      for (const s of rawSteps) {
        if (s.type === 'thinking') {
          thinkingContent += s.content || '';
        }
      }

      if (thinkingContent) {
        const thinkingStep = createStep('thinking', t('agent.chat.thinkingProcess'), 'success');
        thinkingStep.subSteps = [{
          id: `substep-hist-${idx}-thinking`,
          message: thinkingContent,
          timestamp: 0,
        }];
        thinkingStep.status = 'success';
        steps.push(thinkingStep);
      }

      for (const s of rawSteps) {
        if (s.type === 'tool_call') {
          const toolStep = createStep(
            'tool_call',
            t('agent.chat.callingToolStep', { tool: s.toolName || '' }),
            s.error ? 'error' : 'success',
            {
              toolName: s.toolName,
              toolParams: (() => {
                try { return s.toolParams ? JSON.parse(s.toolParams) : {}; } catch { return s.toolParams || {}; }
              })(),
            }
          );
          toolStep.status = 'success';
          steps.push(toolStep);
        } else if (s.type === 'tool_result') {
          const paired = [...steps].reverse().find(
            (st: any) => st.type === 'tool_call' && st.metadata?.toolName === s.toolName
          );
          if (paired) {
            paired.status = s.error ? 'error' : 'success';
            if (paired.metadata) {
              paired.metadata.toolResult = s.toolResult;
              paired.metadata.toolDuration = s.toolDurationMs;
              paired.metadata.toolError = s.error ? s.errorMessage : undefined;
            }
            paired.duration = s.toolDurationMs;
          }
        }
      }

      return {
        iterationNumber: iter.iterationNumber ?? (idx + 1),
        steps,
        status: 'completed' as const,
        startTime: 0,
        endTime: iter.durationMs ?? 0,
        totalDuration: iter.durationMs ?? 0,
        collapsed: true,
      };
    });

    return {
      iterations,
      totalDuration: ep.totalDurationMs ?? 0,
      completedCount: iterations.length,
    };
  };

  /**
   * 映射角色
   */
  const mapRole = (role: string): 'user' | 'assistant' | 'system' => {
    const roleLower = (role || '').toLowerCase();
    if (roleLower === 'user') return 'user';
    if (['assistant', 'ai'].includes(roleLower)) return 'assistant';
    if (roleLower === 'system') return 'system';
    return 'assistant';
  };

  /**
   * 从 metadata 中提取工具调用
   */
  const extractToolCalls = (metadata: any): ToolCall[] | undefined => {
    if (!metadata || typeof metadata !== 'object') return undefined;
    if (Array.isArray(metadata.toolCalls)) return metadata.toolCalls;
    if (metadata.toolName) {
      return [{
        id: metadata.toolExecutionId || `tool-${Date.now()}`,
        name: metadata.toolName,
        params: metadata.toolParams || metadata.params || {},
        result: metadata.toolResult || metadata.result,
        status: metadata.toolStatus || 'success',
        duration: metadata.toolDuration || metadata.duration,
      }];
    }
    return undefined;
  };

  /**
   * 从 metadata 中提取 RAG 结果
   */
  const extractRagResults = (metadata: any): RagResult[] | undefined => {
    if (!metadata || typeof metadata !== 'object') return undefined;
    if (Array.isArray(metadata.ragResults)) return metadata.ragResults;
    if (metadata.knowledgeResult || metadata.ragRetrieve) {
      const result = metadata.knowledgeResult || metadata.ragRetrieve;
      if (Array.isArray(result.documents) && result.documents.length > 0) {
        return result.documents.map((doc: any, index: number) => ({
          id: doc.id || `rag-${index}`,
          content: doc.content || doc.text || '',
          score: doc.score || doc.relevanceScore,
          source: doc.source || doc.fileName || '',
          metadata: doc.metadata || {},
        }));
      }
    }
    return undefined;
  };

  /**
   * 删除消息
   */
  const deleteMessage = (messageId: string) => {
    const index = messages.value.findIndex((msg) => msg.id === messageId);
    if (index !== -1) {
      messages.value.splice(index, 1);
    }
  };

  /**
   * 重新生成
   */
  const regenerate = async () => {
    const userMessages = messages.value.filter((msg) => msg.role === 'user');
    if (userMessages.length === 0) {
      message.warning(t('agent.chat.noRegenMsg'));
      return;
    }

    const lastUserMessage = userMessages[userMessages.length - 1];
    
    const lastAssistantIndex = messages.value.findIndex(
      (msg, index) =>
        msg.role === 'assistant' &&
        index > messages.value.indexOf(lastUserMessage)
    );
    
    if (lastAssistantIndex !== -1) {
      messages.value.splice(lastAssistantIndex, 1);
    }

    await sendMessage(lastUserMessage.content);
  };

  const hasMessages = computed(() => messages.value.length > 0);

  const lastMessage = computed(() => {
    return messages.value.length > 0
      ? messages.value[messages.value.length - 1]
      : null;
  });

  return {
    messages,
    loading,
    currentStatus,
    hasMessages,
    lastMessage,
    sendMessage,
    stopGeneration,
    clearMessages,
    deleteMessage,
    regenerate,
    loadMessages,
    pendingToolConfirmations,
    resolvePendingTool,
    pendingQuestion,
    resolveQuestion,
    // PERSONAL MCP 相关
    pendingSecretRequest,
    resolveSecretRequest,
    skipSecretRequest,
  };
}
