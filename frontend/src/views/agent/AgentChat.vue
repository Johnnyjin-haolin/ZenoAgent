<template>
  <div class="agent-chat-container" :style="brandStyle">
    <!-- 左侧会话列表 -->
    <div v-if="showSlide" class="left-slide" :class="{ collapsed: slideCollapsed }">
      <AgentSlide
        :conversations="conversations"
        :active-id="currentConversationId"
        @select="handleSelectConversation"
        @new="handleNewConversation"
        @update="handleUpdateConversation"
        @delete="handleDeleteConversation"
      />
      
      <!-- 折叠按钮 -->
      <div class="slide-toggle-btn" @click="toggleSlide">
        <Icon :icon="slideCollapsed ? 'ant-design:menu-unfold-outlined' : 'ant-design:menu-fold-outlined'" />
      </div>
    </div>

    <!-- 右侧聊天区域 -->
    <div class="right-chat-area" :class="{ expanded: slideCollapsed }">
      <ChatHeader
        :brand-config="brandConfig"
        :show-brand-title="showBrandTitle"
        :brand-links="brandLinks"
        :brand-version="brandVersion"
        @open-config="showConfigDrawer = true"
      />

      <ChatMessages
        ref="chatMessagesRef"
        :messages="messages"
        :capability-items="capabilityItems"
        :scenario-prompts="scenarioPrompts"
        @apply-prompt="applyScenarioPrompt"
        @confirm-tool="handleConfirmTool"
        @reject-tool="handleRejectTool"
      />

      <ChatInput
        ref="chatInputRef"
        v-model="userInput"
        v-model:selectedModelId="selectedModelId"
        :loading="loading"
        :input-placeholder="inputPlaceholder"
        :current-status="currentStatus"
        @send="handleSend"
        @stop="handleStop"
        @model-change="handleModelChange"
      />

    </div>

    <ChatConfigDrawer
      v-model:open="showConfigDrawer"
      v-model:selectedModelId="selectedModelId"
      v-model:selectedKnowledgeIds="selectedKnowledgeIds"
      v-model:selectedTools="selectedTools"
      v-model:executionMode="executionMode"
      v-model:thinkingConfig="thinkingConfig"
      v-model:ragConfig="ragConfig"
      @model-change="handleModelChange"
      @knowledge-change="handleKnowledgeChange"
      @tools-change="handleToolsChange"
      @thinking-config-change="handleThinkingConfigChange"
      @rag-config-change="handleRagConfigChange"
    />

  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { Icon } from '@/components/Icon';
import { message } from 'ant-design-vue';
import logger from '@/utils/logger';
import { useAgentChat } from './hooks/useAgentChat';
import { useBrandConfig } from './hooks/useBrandConfig';
import { useConversationList } from './hooks/useConversationList';
import { getAvailableModels, getKnowledgeList } from './agent.api';
import { getMcpTools } from './agent.api.adapted';
import ChatConfigDrawer from './components/ChatConfigDrawer.vue';
import ChatHeader from './components/ChatHeader.vue';
import ChatInput from './components/ChatInput.vue';
import ChatMessages from './components/ChatMessages.vue';
import AgentSlide from './components/AgentSlide.vue';
import { AGENT_CONFIG_STORAGE_KEY } from './agent.constants';
import type { ModelInfo, KnowledgeInfo, ThinkingConfig, RAGConfig } from './agent.types';
import { DEFAULT_THINKING_CONFIG, DEFAULT_RAG_CONFIG } from './agent.types';
import { ModelType } from '@/types/model.types';
import type { BrandConfig } from './hooks/useBrandConfig';

declare global {
  interface Window {
    __ZENO_AGENT_BRAND__?: Partial<BrandConfig>;
  }
}

const router = useRouter();

type BrandLink = {
  label: string;
  url: string;
};
const { brandConfig, brandStyle, showBrandTitle, brandLinks, brandVersion, loadBrandConfig } = useBrandConfig();

// 配置抽屉
const showConfigDrawer = ref(false);

// 会话 ID（由会话列表与SSE更新）
const currentConversationId = ref('');

// Agent 配置
const selectedModelId = ref('');
const selectedKnowledgeIds = ref<string[]>([]);
const selectedTools = ref<string[]>([]);
const executionMode = ref<'AUTO' | 'MANUAL'>('AUTO');
const thinkingConfig = ref<ThinkingConfig>({
  conversationHistoryRounds: DEFAULT_THINKING_CONFIG.conversationHistoryRounds,
  maxMessageLength: DEFAULT_THINKING_CONFIG.maxMessageLength,
  actionExecutionHistoryCount: DEFAULT_THINKING_CONFIG.actionExecutionHistoryCount,
});
const ragConfig = ref<RAGConfig>({
  maxResults: DEFAULT_RAG_CONFIG.maxResults,
  minScore: DEFAULT_RAG_CONFIG.minScore,
  maxDocumentLength: DEFAULT_RAG_CONFIG.maxDocumentLength,
  maxTotalContentLength: DEFAULT_RAG_CONFIG.maxTotalContentLength,
  includeInPrompt: DEFAULT_RAG_CONFIG.includeInPrompt,
  enableSmartSummary: DEFAULT_RAG_CONFIG.enableSmartSummary,
});
const isConfigInitialized = ref(false);

type AgentConfigCache = {
  modelId: string;
  knowledgeIds: string[];
  enabledTools: string[];
  mode: 'AUTO' | 'MANUAL';
  thinkingConfig?: ThinkingConfig;
  updatedAt: number;
};

// 使用 Agent Chat Hook
const {
  messages,
  loading,
  currentStatus,
  sendMessage,
  stopGeneration,
  clearMessages,
  loadMessages,
  resolvePendingTool,
} = useAgentChat({
  conversationId: currentConversationId,  // Ref 会自动响应
  defaultModelId: selectedModelId.value,  // 初始值
  defaultKnowledgeIds: selectedKnowledgeIds.value,  // 初始值
  defaultEnabledTools: selectedTools.value,  // 初始值
});

// 用户输入
const userInput = ref('');
const chatMessagesRef = ref<InstanceType<typeof ChatMessages> | null>(null);
const chatInputRef = ref<InstanceType<typeof ChatInput> | null>(null);

// 输入框占位符
const inputPlaceholder = computed(() => {
  if (loading.value) {
    return 'AI 正在回复中...';
  }
  return '请输入您的问题...（Shift + Enter 换行，Enter 发送）';
});

const capabilityItems = [
  {
    icon: '📚',
    title: 'RAG 知识检索',
    desc: '连接企业知识库，检索并引用来源',
  },
  {
    icon: '🧰',
    title: 'MCP 工具调用',
    desc: '调用企业系统工具，支持审批/确认',
  },
  {
    icon: '⚡',
    title: '流式过程可视化',
    desc: '实时展示思考、检索、调用过程',
  },
  {
    icon: '🧠',
    title: '多模型选择',
    desc: '按任务选择合适模型，支持自定义',
  },
];

const scenarioPrompts = [
  '查询设备近7天异常并分析原因',
  '根据知识库输出规范合规检查项',
  '调用工具查询资产信息并总结',
  '检索合同条款并输出风险点',
];

const applyScenarioPrompt = (prompt: string) => {
  userInput.value = prompt;
  nextTick(() => {
    chatInputRef.value?.focusInput();
  });
};

const readConfigCache = (): AgentConfigCache | null => {
  try {
    const raw = localStorage.getItem(AGENT_CONFIG_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return {
      modelId: typeof parsed.modelId === 'string' ? parsed.modelId : '',
      knowledgeIds: Array.isArray(parsed.knowledgeIds) ? parsed.knowledgeIds.filter(Boolean) : [],
      enabledTools: Array.isArray(parsed.enabledTools) ? parsed.enabledTools.filter(Boolean) : [],
      mode: parsed.mode === 'MANUAL' ? 'MANUAL' : 'AUTO',
      thinkingConfig: parsed.thinkingConfig || { ...DEFAULT_THINKING_CONFIG },
      updatedAt: typeof parsed.updatedAt === 'number' ? parsed.updatedAt : Date.now(),
    };
  } catch (error) {
    logger.warn('读取Agent配置缓存失败:', error);
    return null;
  }
};

const persistConfigCache = () => {
  if (!isConfigInitialized.value) return;
  const payload: AgentConfigCache = {
    modelId: selectedModelId.value || '',
    knowledgeIds: [...selectedKnowledgeIds.value],
    enabledTools: [...selectedTools.value],
    mode: executionMode.value,
    thinkingConfig: { ...thinkingConfig.value },
    updatedAt: Date.now(),
  };
  localStorage.setItem(AGENT_CONFIG_STORAGE_KEY, JSON.stringify(payload));
};

const applyCachedConfig = (cache: AgentConfigCache) => {
  selectedModelId.value = cache.modelId || '';
  selectedKnowledgeIds.value = [...cache.knowledgeIds];
  selectedTools.value = [...cache.enabledTools];
  executionMode.value = cache.mode || 'AUTO';
  thinkingConfig.value = cache.thinkingConfig 
    ? { ...cache.thinkingConfig } 
    : { ...DEFAULT_THINKING_CONFIG };
};

const validateConfigWithLatestLists = async () => {
  const [models, knowledgeList, tools] = await Promise.all([
    getAvailableModels(ModelType.CHAT).catch(() => []),
    getKnowledgeList().catch(() => []),
    getMcpTools().catch(() => []),
  ]);

  if (models.length > 0) {
    const modelIds = new Set(models.map((item) => item.id));
    if (selectedModelId.value && !modelIds.has(selectedModelId.value)) {
      const defaultModel = models.find((item) => item.isDefault);
      selectedModelId.value = defaultModel?.id || '';
    }
  }

  if (knowledgeList.length > 0) {
    const knowledgeIdSet = new Set(knowledgeList.map((item) => item.id));
    selectedKnowledgeIds.value = selectedKnowledgeIds.value.filter((id) => knowledgeIdSet.has(id));
  }

  if (tools.length > 0) {
    const toolNameSet = new Set(tools.map((tool) => tool.name));
    selectedTools.value = selectedTools.value.filter((name) => toolNameSet.has(name));
  }
};

const initAgentConfig = async () => {
  const cached = readConfigCache();
  if (cached) {
    applyCachedConfig(cached);
  }
  await validateConfigWithLatestLists();
  isConfigInitialized.value = true;
  persistConfigCache();
};

const scrollToBottom = async () => {
  await chatMessagesRef.value?.scrollToBottom();
};

const {
  conversations,
  showSlide,
  slideCollapsed,
  temporaryConversationId,
  temporaryHasMessages,
  loadConversations,
  handleSelectConversation,
  handleNewConversation,
  handleUpdateConversation,
  handleDeleteConversation,
  toggleSlide,
  markTemporaryHasMessages,
} = useConversationList({
  currentConversationId,
  loadMessages,
  clearMessages,
  scrollToBottom,
  setUserInput: (value) => {
    userInput.value = value;
  },
});

// 发送消息
const handleSend = async () => {
  const content = userInput.value.trim();
  if (!content) {
    return;
  }
  markTemporaryHasMessages();

  // 先清空输入框
  userInput.value = '';
  
  // 等待 DOM 更新，确保输入框已清空
  await nextTick();
  
  // 发送消息
  await sendMessage(content, {
    modelId: selectedModelId.value,
    knowledgeIds: selectedKnowledgeIds.value,
    enabledTools: selectedTools.value,
    mode: executionMode.value,
    thinkingConfig: thinkingConfig.value,
    ragConfig: ragConfig.value,
  });
  
  // 滚动到底部
  await scrollToBottom();
  
  // 确保输入框获得焦点（如果存在）
  await nextTick();
  chatInputRef.value?.focusInput();
};

// 停止生成
const handleStop = () => {
  stopGeneration();
};

// 确认/拒绝工具执行
const handleConfirmTool = async () => {
  await resolvePendingTool(true);
};

const handleRejectTool = async () => {
  await resolvePendingTool(false);
};

// 配置变更处理
const handleModelChange = (modelId: string, model: ModelInfo | null) => {
  logger.debug('模型变更:', modelId, model);
  // 状态已经通过 v-model 双向绑定了，这里只需要处理额外的逻辑
  if (model) {
    message.success(`已切换到模型: ${model.displayName}`);
  }
};

const handleKnowledgeChange = (knowledgeIds: string[], knowledgeList: KnowledgeInfo[]) => {
  logger.debug('知识库变更:', knowledgeIds, knowledgeList);
};

const handleToolsChange = (tools: string[]) => {
  logger.debug('工具变更:', tools);
};

const handleThinkingConfigChange = (config: ThinkingConfig) => {
  logger.debug('思考引擎配置变更:', config);
};

const handleRagConfigChange = (config: RAGConfig) => {
  logger.debug('RAG配置变更:', config);
};

// 初始化
onMounted(() => {
  loadBrandConfig();
  initAgentConfig();
  loadConversations();
});

watch(
  [selectedModelId, selectedKnowledgeIds, selectedTools, executionMode, thinkingConfig],
  () => {
    persistConfigCache();
  },
  { deep: true }
);

</script>

<style scoped lang="less">
.agent-chat-container {
  display: flex;
  height: 100%;
  background: var(--color-background);
  overflow: hidden;
  --brand-primary: var(--google-blue);
}

.left-slide {
  width: 280px;
  height: 100%;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  transition: all 0.3s cubic-bezier(0.4, 0.0, 0.2, 1);
  position: relative;

  &.collapsed {
    width: 0;
    overflow: hidden;
    border-right: none;
  }

  .slide-toggle-btn {
    position: absolute;
    top: 50%;
    right: -12px;
    transform: translateY(-50%);
    width: 24px;
    height: 48px;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: 0 8px 8px 0;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    z-index: 10;
    transition: all 0.2s;
    box-shadow: 2px 0 4px rgba(0,0,0,0.05);

    &:hover {
      background: var(--color-surface-hover);
    }
  }
}

.right-chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  overflow: hidden;
  background: var(--color-background);

  &.expanded {
    margin-left: 0;
  }
}
</style>

