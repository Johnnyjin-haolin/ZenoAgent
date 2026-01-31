/**
 * Agent 聊天逻辑 Hook
 * @author JeecG Team
 * @date 2025-11-30
 */

import { ref, Ref, computed, reactive, nextTick } from 'vue';
import { message } from 'ant-design-vue';
import { executeAgent, getConversationMessages, confirmToolExecution, stopAgent } from '../agent.api';
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
  ThinkingConfig,
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
      statusText: '准备中...',
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
    };

    try {
      // 执行 Agent 任务
      currentController = await executeAgent(request, {
        onStart: (event) => {
          console.log('任务开始:', event);
          updateConversationId(event);
          // 【新增】保存 requestId 用于停止功能
          currentRequestId = event.requestId || null;
          assistantMessage.status = 'thinking';
          assistantMessage.statusText = '开始处理...';
          currentStatus.value = '任务已启动';
        },

        onIterationStart: (event) => {
          console.log('迭代开始:', event);
          updateConversationId(event);
          
          const iterationNumber = event.data?.iterationNumber || 1;
          
          // 创建新迭代
          const newIteration: any = reactive({
            iterationNumber,
            steps: [],
            status: 'running',
            startTime: Date.now(),
            collapsed: false,  // 默认展开
          });
          
          assistantMessage.process!.iterations.push(newIteration);
          currentIteration = newIteration;
          
          console.log(`🔁 创建第 ${iterationNumber} 轮迭代（展开）`);
        },

        onThinking: (event) => {
          console.log('AI 思考中:', event);
          updateConversationId(event);
          assistantMessage.status = 'thinking';
          assistantMessage.statusText = event.message || '思考中...';
          currentStatus.value = event.message || '思考中...';

          if (!currentIteration) return;

          // 在当前迭代添加或更新思考步骤
          let thinkingStep = currentIteration.steps.find((s: any) => s.type === 'thinking');
          if (!thinkingStep) {
            thinkingStep = createStep('thinking', '思考与规划', 'running');
            thinkingStep.subSteps = [];
            currentIteration.steps.push(thinkingStep);
          }

          // 添加子步骤
          const subStep = {
            id: `substep-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            message: event.message || '思考中...',
            timestamp: Date.now(),
          };
          
          if (!thinkingStep.subSteps) {
            thinkingStep.subSteps = [];
          }
          thinkingStep.subSteps.push(subStep);
        },

        onModelSelected: (event) => {
          console.log('模型已选择:', event);
          updateConversationId(event);
          if (event.data) {
            assistantMessage.model = event.data.name || event.data.id;
          }
          currentStatus.value = `使用模型: ${event.data?.name || ''}`;
        },

        onRagRetrieve: (event) => {
          console.log('RAG 检索:', event);
          updateConversationId(event);
          assistantMessage.status = 'retrieving';
          assistantMessage.statusText = '正在检索知识库...';
          currentStatus.value = '检索知识库中...';

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
          currentIteration.steps.push(step);
        },

        onToolCall: (event) => {
          console.log('工具调用:', event);
          updateConversationId(event);
          const requiresConfirmation = Boolean(event.data?.requiresConfirmation);
          assistantMessage.status = 'calling_tool';
          assistantMessage.statusText = requiresConfirmation
            ? `等待确认: ${event.data?.toolName || ''}`
            : `调用工具: ${event.data?.toolName || ''}`;
          currentStatus.value = assistantMessage.statusText || '';

          if (!currentIteration) return;

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
            const step = createStep('tool_call', `调用工具: ${event.data.toolName}`, stepStatus, {
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
          console.log('工具结果:', event);
          updateConversationId(event);
          currentStatus.value = '工具执行完成';

          // 更新最后一个工具调用的结果
          if (event.data && assistantMessage.toolCalls && assistantMessage.toolCalls.length > 0) {
            const lastTool = assistantMessage.toolCalls[assistantMessage.toolCalls.length - 1];
            if (lastTool && lastTool.name === event.data.toolName) {
              lastTool.result = event.data.result;
              lastTool.status = event.data.error ? 'error' : 'success';
              if (event.data.error) {
                lastTool.error = event.data.error;
              }

              // 更新当前迭代中的工具调用步骤
              if (currentIteration && currentIteration.steps.length > 0) {
                const steps = currentIteration.steps;
                const toolStep = steps.reverse().find(
                  (step: any) => step.type === 'tool_call' && step.metadata?.toolName === event.data.toolName
                );
                steps.reverse(); // 恢复原顺序
                
                if (toolStep) {
                  toolStep.status = event.data.error ? 'error' : 'success';
                  toolStep.endTime = Date.now();
                  toolStep.duration = toolStep.startTime ? toolStep.endTime - toolStep.startTime : undefined;
                  if (toolStep.metadata) {
                    toolStep.metadata.toolResult = event.data.result;
                    toolStep.metadata.toolError = event.data.error;
                  }
                }
              }
            }
          }
        },

        onMessage: (event) => {
          // 流式内容
          console.log('[useAgentChat] 收到消息片段:', event.content);
          updateConversationId(event);
          
          // 标记流式输出已开始
          if (!assistantMessage.process!.streamingStarted) {
            assistantMessage.process!.streamingStarted = true;
            // 完成思考阶段
            if (currentIteration && currentIteration.thinkingPhase && !currentIteration.thinkingPhase.duration) {
              currentIteration.thinkingPhase.duration = Date.now() - currentIteration.thinkingPhase.startTime;
            }
          }
          
          assistantMessage.status = 'generating';
          assistantMessage.statusText = '';
          assistantMessage.loading = true;
          assistantMessage.content += event.content || '';
          
          currentStatus.value = '';

          if (!currentIteration) return;

          // 完成当前迭代的所有运行中的步骤（除了生成步骤）
          currentIteration.steps.forEach((step: any) => {
            if (step.status === 'running' && step.type !== 'generating') {
              step.status = 'success';
              step.endTime = Date.now();
              step.duration = step.startTime ? step.endTime - step.startTime : undefined;
            }
          });

          // 添加生成回答步骤（只添加一次）
          const generatingStep = currentIteration.steps.find((s: any) => s.type === 'generating');
          if (!generatingStep) {
            const step = createStep('generating', '生成回答', 'running');
            currentIteration.steps.push(step);
          }
        },

        onIterationEnd: (event) => {
          console.log('迭代结束:', event);
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

          // 更新迭代状态
          currentIteration.status = 'completed';
          currentIteration.endTime = Date.now();
          currentIteration.totalDuration = event.data?.durationMs || 
            (currentIteration.endTime - currentIteration.startTime);
          currentIteration.shouldContinue = event.data?.shouldContinue;
          currentIteration.terminationReason = event.data?.terminationReason;
          currentIteration.terminationMessage = event.data?.message;

          // 自动折叠已完成的迭代
          currentIteration.collapsed = true;

          console.log(`🔁 完成第 ${currentIteration.iterationNumber} 轮迭代（自动折叠）`);

          // 如果不继续迭代，清空 currentIteration
          if (!event.data?.shouldContinue) {
            currentIteration = null;
          }
        },

        onStreamComplete: (event) => {
          // 流式输出完成（所有 token 已发送）
          console.log('[useAgentChat] 流式输出完成');
          updateConversationId(event);
          
          // 更新状态：流式输出完成，但任务还未完全结束
          assistantMessage.status = 'done';
          assistantMessage.loading = false;
          currentStatus.value = '';
        },

        onComplete: (event) => {
          console.log('任务完成:', event);
          updateConversationId(event);
          assistantMessage.status = 'done';
          assistantMessage.statusText = '';
          assistantMessage.loading = false;
          assistantMessage.tokens = event.totalTokens;
          assistantMessage.duration = event.duration;

          // 更新执行过程统计
          assistantMessage.process!.totalDuration = event.duration;
          assistantMessage.process!.completedCount = assistantMessage.process!.iterations.filter(
            (iter: any) => iter.status === 'completed'
          ).length;

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
          updateConversationId(event);
          assistantMessage.status = 'error';
          assistantMessage.statusText = '';
          assistantMessage.loading = false;
          assistantMessage.error = true;
          assistantMessage.content = event.message || '处理失败，请稍后重试';
          
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
            currentIteration.status = 'completed';
            currentIteration.endTime = Date.now();
            currentIteration.totalDuration = currentIteration.endTime - currentIteration.startTime;
            currentIteration.shouldContinue = false;
            currentIteration.terminationReason = 'EXCEPTION';
            currentIteration.terminationMessage = `执行出错: ${event.message || '未知错误'}`;
            currentIteration.collapsed = true;
          }

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
  const stopGeneration = async () => {
    if (currentController) {
      try {
        // 1. 先调用后端停止接口（重要：先告诉后端停止）
        if (currentRequestId) {
          console.log('调用后端停止接口:', currentRequestId);
          const success = await stopAgent(currentRequestId);
          console.log('后端停止结果:', success);
        }
        
        // 2. 等待一小段时间让后端处理
        await new Promise(resolve => setTimeout(resolve, 100));
        
        // 3. 再中止前端 SSE 连接
        currentController.abort();
      } catch (error) {
        console.error('停止失败:', error);
      } finally {
        currentController = null;
        currentRequestId = null;
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
      message.error('工具确认失败，请重试');
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
              toolError: '用户拒绝执行',
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
      
      console.log(`已加载 ${messages.value.length} 条历史消息`);
    } catch (error) {
      console.error('加载历史消息失败:', error);
      message.error('加载历史消息失败');
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
    loadMessages,
    pendingToolConfirmations,
    resolvePendingTool,
  };
}

