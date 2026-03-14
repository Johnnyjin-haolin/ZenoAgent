/**
 * Agent 聊天逻辑 Hook
 * @date 2025-11-30
 */

import { ref, Ref, computed, reactive, nextTick } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import logger from '@/utils/logger';
import { executeAgent, getConversationMessages, confirmToolExecution, stopAgent, submitAnswer } from '../agent.api';
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
  ThinkingConfig,
  RAGConfig,
  UserQuestion,
} from '../agent.types';

export interface UseAgentChatOptions {
  /** 会话ID */
  conversationId?: Ref<string>;
  /** 默认模型ID */
  defaultModelId?: string;
  /** 默认知识库IDs */
  defaultKnowledgeIds?: string[];
  /** 默认启用的工具 */
  defaultEnabledTools?: string[];
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
    defaultEnabledTools = [],
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
      // 查找特定工具的调用步骤（允许waiting/running）
      return [...steps].reverse().find((s) =>
        s.type === type && s.metadata?.toolName === toolName && ['waiting', 'running'].includes(s.status)
      );
    }
    // 查找最后一个该类型的步骤
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
    const message = event.message || '';
    const data = event.data || {};

    // 1. 检查是否包含规划信息
    if (data.steps && Array.isArray(data.steps)) {
      return {
        type: 'plan' as const,
        planInfo: {
          planId: data.planId,
          taskType: data.taskType,
          steps: data.steps,
          variables: data.variables,
        },
        message: message,
      };
    }

    // 2. 检查是否是步骤描述（如"步骤 1/3: 检索相关知识"）
    const stepMatch = message.match(/步骤\s*(\d+)\/(\d+):\s*(.+)/);
    if (stepMatch) {
      return {
        type: 'step' as const,
        stepProgress: {
          current: parseInt(stepMatch[1]),
          total: parseInt(stepMatch[2]),
          description: stepMatch[3],
        },
        message: message,
      };
    }

    // 3. 普通思考消息
    return {
      type: 'thinking' as const,
      message: message,
    };
  };

  /**
   * 发送消息
   */
  const sendMessage = async (
    content: string,
    options: {
      modelId?: string;
      knowledgeIds?: string[];
      enabledTools?: string[];
      mode?: 'AUTO' | 'MANUAL';
      thinkingConfig?: ThinkingConfig;
      ragConfig?: RAGConfig;
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
      process: {
        iterations: [],
        completedCount: 0,
        streamingStarted: false, // 流式输出是否已开始
      },
    });
    messages.value.push(assistantMessage);

    // 当前迭代对象引用
    let currentIteration: any = null;

    // 构建请求
    const request: AgentRequest = {
      content: content.trim(),
      conversationId: conversationId?.value,
      modelId: options.modelId || defaultModelId,
      knowledgeIds: options.knowledgeIds || defaultKnowledgeIds,
      enabledTools: options.enabledTools || defaultEnabledTools,
      mode: options.mode || 'AUTO',
      thinkingConfig: options.thinkingConfig,
      ragConfig: options.ragConfig,
    };

    try {
      // 执行 Agent 任务
      currentController = await executeAgent(request, {
        onAskUserQuestion: (question) => {
          logger.debug('[useAgentChat] 收到用户提问:', question);
          pendingQuestion.value = question;

          // 在当前迭代中标记有提问步骤（展示用）
          if (currentIteration) {
            const step = createStep('tool_call', t('agent.chat.askingUser', { question: question.question }), 'waiting', {
              toolName: 'system_ask_user_question',
            });
            currentIteration.steps.push(step);
          }
        },

        onStart: (event) => {
          logger.debug('任务开始:', event);
          updateConversationId(event);
          // 【新增】保存 requestId 用于停止功能
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
          
          // 清空 statusText，让 AgentMessage 组件根据 status 使用国际化文案
          assistantMessage.statusText = undefined;
          
          // 更新全局状态文本（手动处理国际化）
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

          // 在当前迭代添加或更新思考步骤
          let thinkingStep = currentIteration.steps.find((s: any) => s.type === 'thinking');
          if (!thinkingStep) {
            thinkingStep = createStep('thinking', t('agent.chat.thinkingProcess'), 'running');
            thinkingStep.subSteps = [];
            currentIteration.steps.push(thinkingStep);
          }

          // 思考完成后收起思考步骤
          if (event.message && event.message.includes('思考完成')) {
            thinkingStep.expanded = false;
          }
        },

        onThinkingDelta: (event) => {
          logger.debug('AI 思考片段:', event.content);
          updateConversationId(event);
          assistantMessage.status = 'thinking';

          if (!currentIteration) return;

          // 中间推理 token：追加到当前迭代的思考步骤
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

          // 完成思考步骤
          const thinkingStep = currentIteration.steps.find((s: any) => s.type === 'thinking');
          if (thinkingStep && thinkingStep.status === 'running') {
            thinkingStep.status = 'success';
            thinkingStep.endTime = Date.now();
            thinkingStep.duration = thinkingStep.endTime - (thinkingStep.startTime || 0);
          }

          // 保存检索结果
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

          // 添加检索步骤
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
          const requiresConfirmation = Boolean(event.data?.requiresConfirmation);
          assistantMessage.status = 'calling_tool';
          assistantMessage.statusText = requiresConfirmation
            ? t('agent.chat.waitingConfirm', { tool: event.data?.toolName || '' })
            : t('agent.chat.callingToolStep', { tool: event.data?.toolName || '' });
          currentStatus.value = assistantMessage.statusText || '';

          // 兜底：若迭代还未创建，自动创建一个（lazy create）
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

          // 完成思考步骤
          const thinkingStep = currentIteration.steps.find((s: any) => s.type === 'thinking');
          if (thinkingStep && thinkingStep.status === 'running') {
            thinkingStep.status = 'success';
            thinkingStep.endTime = Date.now();
            thinkingStep.duration = thinkingStep.endTime - (thinkingStep.startTime || 0);
          }

          // 添加工具调用记录
          if (event.data && event.data.toolName) {
            const toolCall: ToolCall = {
              name: event.data.toolName,
              params: event.data.params || {},
              status: 'pending',
            };
            assistantMessage.toolCalls?.push(toolCall);

            // 添加工具调用步骤
            const stepStatus: ProcessStepStatus = requiresConfirmation ? 'waiting' : 'running';
            const step = createStep('tool_call', t('agent.chat.callingToolStep', { tool: event.data.toolName }), stepStatus, {
              toolName: event.data.toolName,
              toolParams: event.data.params || {},
              requiresConfirmation,
            });
            currentIteration.steps.push(step);

            if (requiresConfirmation && event.data.toolExecutionId) {
              pendingToolConfirmations.value.push({
                requestId: event.requestId,
                toolExecutionId: event.data.toolExecutionId,
                toolName: event.data.toolName,
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

          // 更新 toolCalls 记录（用于消息气泡内的工具列表）
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

          // 倒序遍历查找对应工具步骤（避免 reverse() 原地修改响应式数组）
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
            // 优先使用后端传回的精准耗时，兜底用前端计时
            toolStep.duration = duration ?? (toolStep.startTime ? toolStep.endTime - toolStep.startTime : undefined);
            if (toolStep.metadata) {
              toolStep.metadata.toolResult = result;
              toolStep.metadata.toolError = error;
              toolStep.metadata.toolDuration = duration;
            }
          }
        },

        onMessage: (event) => {
          // 最终回复 token：直接追加到 content
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

          // 完成当前迭代的所有运行中的步骤
          currentIteration.steps.forEach((step: any) => {
            if (step.status === 'running') {
              step.status = 'success';
              step.endTime = Date.now();
              step.duration = step.startTime ? step.endTime - step.startTime : undefined;
            }
          });

          // 更新迭代状态并折叠
          currentIteration.status = 'completed';
          currentIteration.endTime = Date.now();
          currentIteration.totalDuration = currentIteration.endTime - currentIteration.startTime;
          currentIteration.collapsed = true;

          logger.debug(`🔁 完成第 ${currentIteration.iterationNumber} 轮迭代（自动折叠）`);
          currentIteration = null;
        },

        onStreamComplete: (event) => {
          // 流式输出完成（所有 token 已发送）
          logger.debug('[useAgentChat] 流式输出完成');
          updateConversationId(event);
          
          // 更新状态：流式输出完成，但任务还未完全结束
          assistantMessage.status = 'done';
          assistantMessage.loading = false;
          currentStatus.value = '';
        },

        onComplete: (event) => {
          logger.debug('任务完成:', event);
          updateConversationId(event);
          
          // 【新增】兜底：强制完成所有running状态的迭代和步骤
          if (assistantMessage.process && assistantMessage.process.iterations) {
            assistantMessage.process.iterations.forEach((iter: any) => {
              if (iter.status === 'running') {
                logger.warn(`⚠️ 发现未完成的迭代 ${iter.iterationNumber}，强制标记为completed`);
                
                // 完成所有运行中的步骤
                iter.steps?.forEach((step: any) => {
                  if (step.status === 'running') {
                    step.status = 'success';
                    step.endTime = Date.now();
                    step.duration = step.startTime ? step.endTime - step.startTime : undefined;
                  }
                });
                
                // 标记迭代完成
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

          // 更新执行过程统计
          assistantMessage.process!.totalDuration = event.duration;
          assistantMessage.process!.completedCount = assistantMessage.process!.iterations.filter(
            (iter: any) => iter.status === 'completed'
          ).length;

          loading.value = false;
          currentStatus.value = t('agent.chat.finish');
          currentController = null;

          // 更新会话ID（如果返回了新的会话ID）
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
          
          // 将当前迭代的所有运行中的步骤标记为错误
          if (currentIteration && currentIteration.steps.length > 0) {
            currentIteration.steps.forEach((step: any) => {
              if (step.status === 'running') {
                step.status = 'error';
                step.endTime = Date.now();
                step.duration = step.startTime ? step.endTime - step.startTime : undefined;
                step.metadata = { ...step.metadata, errorMessage: event.message };
              }
            });
            
            // 标记迭代完成
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
        // 1. 先调用后端停止接口（重要：先告诉后端停止）
        if (currentRequestId) {
          logger.debug('调用后端停止接口:', currentRequestId);
          const success = await stopAgent(currentRequestId);
          logger.debug('后端停止结果:', success);
        }
        
        // 2. 等待一小段时间让后端处理
        await new Promise(resolve => setTimeout(resolve, 100));
        
        // 3. 再中止前端 SSE 连接
        currentController.abort();
      } catch (error) {
        logger.error('停止失败:', error);
      } finally {
        currentController = null;
        currentRequestId = null;
        loading.value = false;
        currentStatus.value = t('agent.chat.stopGen');
        
        // 更新最后一条助手消息状态
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
   * 由 AgentUserQuestion 组件在用户填写完毕后调用
   */
  const resolveQuestion = async (answer: string) => {
    if (!pendingQuestion.value) return;

    const { questionId } = pendingQuestion.value;
    const questionText = pendingQuestion.value.question;

    // 完成当前迭代中 ask_user_question 的步骤
    const lastAssistantMsg = messages.value[messages.value.length - 1];
    if (lastAssistantMsg?.process?.iterations?.length) {
      const iter = lastAssistantMsg.process.iterations[lastAssistantMsg.process.iterations.length - 1] as any;
      if (iter?.steps) {
        const step = [...iter.steps].reverse().find(
          (s: any) => s.type === 'tool_call' && s.metadata?.toolName === 'system_ask_user_question'
        );
        if (step) {
          step.status = 'success';
          step.endTime = Date.now();
          step.duration = step.startTime ? step.endTime - step.startTime : undefined;
          step.metadata = { ...step.metadata, toolResult: answer };
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
      // 获取当前正在进行的迭代（最后一个迭代）
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
      // 调用API获取消息
      const rawMessages = await getConversationMessages(conversationId);
      
      // 转换为前端格式
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
    return {
      id: raw.id || raw.messageId || `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      role: mapRole(raw.role),
      content: raw.content || '',
      datetime: raw.createTime || new Date().toISOString(),
      status: 'done', // 历史消息都是已完成状态
      model: raw.modelId,
      tokens: raw.tokens,
      duration: raw.duration,
      // 从 metadata 中提取工具调用和RAG结果
      toolCalls: extractToolCalls(raw.metadata),
      ragResults: extractRagResults(raw.metadata),
    };
  };

  /**
   * 映射角色
   */
  const mapRole = (role: string): 'user' | 'assistant' | 'system' => {
    const roleLower = (role || '').toLowerCase();
    if (roleLower === 'user' || roleLower === 'USER') {
      return 'user';
    } else if (roleLower === 'assistant' || roleLower === 'ai' || roleLower === 'ASSISTANT' || roleLower === 'AI') {
      return 'assistant';
    } else if (roleLower === 'system' || roleLower === 'SYSTEM') {
      return 'system';
    }
    // 默认返回 assistant
    return 'assistant';
  };

  /**
   * 从 metadata 中提取工具调用
   */
  const extractToolCalls = (metadata: any): ToolCall[] | undefined => {
    if (!metadata || typeof metadata !== 'object') {
      return undefined;
    }

    // 如果 metadata 中有 toolCalls 字段
    if (Array.isArray(metadata.toolCalls)) {
      return metadata.toolCalls;
    }

    // 如果 metadata 中有 toolName 和 toolParams，构造一个 ToolCall
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
    if (!metadata || typeof metadata !== 'object') {
      return undefined;
    }

    // 如果 metadata 中有 ragResults 字段
    if (Array.isArray(metadata.ragResults)) {
      return metadata.ragResults;
    }

    // 如果 metadata 中有 knowledgeResult，构造 RAG 结果
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
    // 找到最后一条用户消息
    const userMessages = messages.value.filter((msg) => msg.role === 'user');
    if (userMessages.length === 0) {
      message.warning(t('agent.chat.noRegenMsg'));
      return;
    }

    const lastUserMessage = userMessages[userMessages.length - 1];
    
    // 删除最后一条助手消息
    const lastAssistantIndex = messages.value.findIndex(
      (msg, index) =>
        msg.role === 'assistant' &&
        index > messages.value.indexOf(lastUserMessage)
    );
    
    if (lastAssistantIndex !== -1) {
      messages.value.splice(lastAssistantIndex, 1);
    }

    // 重新发送
    await sendMessage(lastUserMessage.content);
  };

  // 是否有消息
  const hasMessages = computed(() => messages.value.length > 0);

  // 最后一条消息
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
  };
}
