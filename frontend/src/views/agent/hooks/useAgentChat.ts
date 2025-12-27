/**
 * Agent 聊天逻辑 Hook
 * @author JeecG Team
 * @date 2025-11-30
 */

import { ref, Ref, computed, reactive } from 'vue';
import { message } from 'ant-design-vue';
import { executeAgent } from '../agent.api';
import type {
  AgentMessage,
  AgentRequest,
  ToolCall,
  RagResult,
  ProcessStep,
  ProcessStepType,
  ProcessStepStatus,
  PlanInfo,
  ProcessSubStep,
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
      expanded: false,
      metadata: metadata || {},
    };
  };

  /**
   * 查找步骤
   */
  const findStep = (steps: ProcessStep[], type: ProcessStepType, toolName?: string): ProcessStep | undefined => {
    if (type === 'tool_call' && toolName) {
      // 查找特定工具的调用步骤
      return steps.find((s) => s.type === type && s.metadata?.toolName === toolName && s.status === 'running');
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
      images?: string[];
    } = {}
  ) => {
    if (loading.value) {
      message.warning('请等待当前消息处理完成');
      return;
    }

    if (!content.trim()) {
      message.warning('请输入消息内容');
      return;
    }

    loading.value = true;
    currentStatus.value = '准备发送...';

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
      statusText: '准备中...',
      loading: true,
      toolCalls: [],
      ragResults: [],
      process: {
        collapsed: false, // 默认展开（流式输出前展开）
        steps: [],
        completedCount: 0,
        streamingStarted: false, // 流式输出是否已开始
      },
    });
    messages.value.push(assistantMessage);

    // 构建请求
    const request: AgentRequest = {
      content: content.trim(),
      conversationId: conversationId?.value,
      modelId: options.modelId || defaultModelId,
      knowledgeIds: options.knowledgeIds || defaultKnowledgeIds,
      enabledTools: options.enabledTools || defaultEnabledTools,
      mode: options.mode || 'AUTO',
    };

    try {
      // 执行 Agent 任务
      currentController = await executeAgent(request, {
        onStart: (event) => {
          console.log('任务开始:', event);
          assistantMessage.status = 'thinking';
          assistantMessage.statusText = '开始处理...';
          currentStatus.value = '任务已启动';
        },

        onThinking: (event) => {
          console.log('AI 思考中:', event);
          assistantMessage.status = 'thinking';
          assistantMessage.statusText = event.message || '思考中...';
          currentStatus.value = event.message || '思考中...';

          const steps = assistantMessage.process!.steps;
          let thinkingStep = findStep(steps, 'thinking');

          // 如果思考步骤不存在，创建一个
          if (!thinkingStep) {
            thinkingStep = createStep('thinking', '思考与规划', 'running');
            thinkingStep.subSteps = [];
            steps.push(thinkingStep);
          }

          // 解析消息类型
          const parsed = parseThinkingMessage(event);

          // 创建子步骤
          const subStep: ProcessSubStep = {
            id: `substep-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            message: parsed.message,
            timestamp: Date.now(),
          };

          // 如果包含规划信息
          if (parsed.type === 'plan' && parsed.planInfo) {
            subStep.hasPlan = true;
            thinkingStep.planInfo = parsed.planInfo;
            
            // 更新步骤名称，显示规划步骤数量
            if (parsed.planInfo.steps && parsed.planInfo.steps.length > 0) {
              thinkingStep.name = `思考与规划 (已规划 ${parsed.planInfo.steps.length} 个执行步骤)`;
            }
          }

          // 如果包含步骤进度信息
          if (parsed.type === 'step' && parsed.stepProgress) {
            subStep.stepProgress = parsed.stepProgress;
            
            // 更新思考步骤的步骤进度
            thinkingStep.stepProgress = {
              current: parsed.stepProgress.current,
              total: parsed.stepProgress.total,
            };
          }

          // 添加子步骤
          if (!thinkingStep.subSteps) {
            thinkingStep.subSteps = [];
          }
          thinkingStep.subSteps.push(subStep);
        },

        onModelSelected: (event) => {
          console.log('模型已选择:', event);
          if (event.data) {
            assistantMessage.model = event.data.name || event.data.id;
          }
          currentStatus.value = `使用模型: ${event.data?.name || ''}`;
        },

        onRagRetrieve: (event) => {
          console.log('RAG 检索:', event);
          assistantMessage.status = 'retrieving';
          assistantMessage.statusText = '正在检索知识库...';
          currentStatus.value = '检索知识库中...';

          const steps = assistantMessage.process!.steps;

          // 完成思考步骤
          finishStep(steps, 'thinking', 'success');

          // 保存检索结果
          let ragResults: RagResult[] = [];
          let retrieveCount = 0;
          let avgScore = 0;

          if (event.data) {
            if (Array.isArray(event.data)) {
              // 如果返回的是数组
              ragResults = event.data;
              retrieveCount = event.data.length;
            } else if (event.data.knowledgeIds) {
              // 如果返回的是对象，包含 knowledgeIds
              retrieveCount = event.data.resultCount || 0;
              avgScore = event.data.avgScore || 0;
              ragResults.push({
                content: `检索到 ${retrieveCount} 条相关知识，平均分数: ${avgScore}`,
                score: avgScore,
                source: event.data.query,
              });
            }
            
            assistantMessage.ragResults = ragResults;
          }

          // 添加检索步骤
          const step = createStep('rag_retrieve', '检索知识库', 'running', {
            retrieveCount,
            avgScore,
            ragResults,
          });
          steps.push(step);
        },

        onToolCall: (event) => {
          console.log('工具调用:', event);
          assistantMessage.status = 'calling_tool';
          assistantMessage.statusText = `调用工具: ${event.data?.toolName || ''}`;
          currentStatus.value = `调用工具: ${event.data?.toolName || ''}`;

          const steps = assistantMessage.process!.steps;

          // 完成 RAG 检索步骤（如果有）
          finishStep(steps, 'rag_retrieve', 'success');

          // 完成思考步骤（如果还没完成）
          const thinkingStep = findStep(steps, 'thinking');
          if (thinkingStep && thinkingStep.status === 'running') {
            finishStep(steps, 'thinking', 'success');
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
            const step = createStep('tool_call', `调用工具: ${event.data.toolName}`, 'running', {
              toolName: event.data.toolName,
              toolParams: event.data.params || {},
            });
            steps.push(step);
          }
        },

        onToolResult: (event) => {
          console.log('工具结果:', event);
          currentStatus.value = '工具执行完成';

          const steps = assistantMessage.process!.steps;

          // 更新最后一个工具调用的结果
          if (event.data && assistantMessage.toolCalls && assistantMessage.toolCalls.length > 0) {
            const lastTool = assistantMessage.toolCalls[assistantMessage.toolCalls.length - 1];
            if (lastTool && lastTool.name === event.data.toolName) {
              lastTool.result = event.data.result;
              lastTool.status = event.data.error ? 'error' : 'success';
              if (event.data.error) {
                lastTool.error = event.data.error;
              }

              // 完成工具调用步骤
              finishStep(
                steps,
                'tool_call',
                event.data.error ? 'error' : 'success',
                {
                  toolResult: event.data.result,
                  toolError: event.data.error,
                },
                event.data.toolName
              );
            }
          }
        },

        onMessage: (event) => {
          // 流式内容
          console.log('[useAgentChat] 收到消息片段:', event.content);
          
          // 标记流式输出已开始
          if (!assistantMessage.process!.streamingStarted) {
            assistantMessage.process!.streamingStarted = true;
            // 完成思考步骤（如果还在运行）
            const thinkingStep = findStep(assistantMessage.process!.steps, 'thinking');
            if (thinkingStep && thinkingStep.status === 'running') {
              finishStep(assistantMessage.process!.steps, 'thinking', 'success');
            }
          }
          
          assistantMessage.status = 'generating';
          assistantMessage.statusText = '';
          assistantMessage.loading = true;
          assistantMessage.content += event.content || '';
          
          currentStatus.value = '';

          const steps = assistantMessage.process!.steps;

          // 完成所有运行中的步骤（除了生成步骤）
          steps.forEach((step) => {
            if (step.status === 'running' && step.type !== 'generating') {
              step.status = 'success';
              step.endTime = Date.now();
              step.duration = step.startTime ? step.endTime - step.startTime : undefined;
            }
          });

          // 添加生成回答步骤（只添加一次）
          const generatingStep = findStep(steps, 'generating');
          if (!generatingStep) {
            const step = createStep('generating', '生成回答', 'running');
            steps.push(step);
          }
        },

        onComplete: (event) => {
          console.log('任务完成:', event);
          assistantMessage.status = 'done';
          assistantMessage.statusText = '';
          assistantMessage.loading = false;
          assistantMessage.tokens = event.totalTokens;
          assistantMessage.duration = event.duration;
          
          const steps = assistantMessage.process!.steps;

          // 完成生成步骤
          finishStep(steps, 'generating', 'success');

          // 添加完成步骤
          const completeStep = createStep('complete', '完成', 'success');
          completeStep.startTime = Date.now();
          completeStep.endTime = Date.now();
          completeStep.duration = 0;
          steps.push(completeStep);

          // 更新执行过程统计
          assistantMessage.process!.totalDuration = event.duration;
          assistantMessage.process!.completedCount = steps.filter(
            (s) => s.status === 'success' || s.status === 'error' || s.status === 'skipped'
          ).length;

          // 流式输出结束后，自动折叠执行过程
          if (assistantMessage.process!.streamingStarted) {
            assistantMessage.process!.collapsed = true;
          }

          loading.value = false;
          currentStatus.value = '';
          currentController = null;

          // 更新会话ID（如果返回了新的会话ID）
          if (event.data?.conversationId && conversationId) {
            conversationId.value = event.data.conversationId;
          }

          message.success('回答完成');
        },

        onError: (event) => {
          console.error('发生错误:', event);
          assistantMessage.status = 'error';
          assistantMessage.statusText = '';
          assistantMessage.loading = false;
          assistantMessage.error = true;
          assistantMessage.content = event.message || '处理失败，请稍后重试';
          
          const steps = assistantMessage.process!.steps;

          // 将所有运行中的步骤标记为错误
          steps.forEach((step) => {
            if (step.status === 'running') {
              step.status = 'error';
              step.endTime = Date.now();
              step.duration = step.startTime ? step.endTime - step.startTime : undefined;
              step.metadata = { ...step.metadata, errorMessage: event.message };
            }
          });

          loading.value = false;
          currentStatus.value = '';
          currentController = null;

          message.error(event.message || '处理失败');
        },
      });
    } catch (error: any) {
      console.error('发送消息失败:', error);
      assistantMessage.status = 'error';
      assistantMessage.loading = false;
      assistantMessage.error = true;
      assistantMessage.content = '发送失败，请稍后重试';
      
      loading.value = false;
      currentStatus.value = '';
      currentController = null;

      message.error('发送失败，请稍后重试');
    }
  };

  /**
   * 停止生成
   */
  const stopGeneration = () => {
    if (currentController) {
      currentController.abort();
      currentController = null;
      loading.value = false;
      currentStatus.value = '';
      
      // 更新最后一条助手消息状态
      const lastMessage = messages.value[messages.value.length - 1];
      if (lastMessage && lastMessage.role === 'assistant') {
        lastMessage.status = 'done';
        lastMessage.loading = false;
        lastMessage.statusText = '';
      }
      
      message.info('已停止生成');
    }
  };

  /**
   * 清空消息
   */
  const clearMessages = () => {
    messages.value = [];
    currentStatus.value = '';
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
      message.warning('没有可重新生成的消息');
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
  };
}

