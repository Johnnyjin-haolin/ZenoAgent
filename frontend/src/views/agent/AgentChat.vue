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
    </div>

    <!-- 折叠按钮 (Moved outside) -->
    <div 
      v-if="showSlide"
      class="slide-toggle-btn" 
      :class="{ collapsed: slideCollapsed }"
      @click="toggleSlide"
    >
      <Icon :icon="slideCollapsed ? 'ant-design:menu-unfold-outlined' : 'ant-design:menu-fold-outlined'" />
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

      <!-- Agent 选择器 -->
      <div class="agent-selector-bar">
        <span class="agent-selector-label">Agent：</span>
        <AgentSelector
          v-model="selectedAgentId"
          @change="handleAgentChange"
        />
      </div>

      <ChatMessages
        ref="chatMessagesRef"
        :messages="messages"
        :capability-items="capabilityItems"
        :scenario-prompts="scenarioPrompts"
        @apply-prompt="applyScenarioPrompt"
        @confirm-tool="handleConfirmTool"
        @reject-tool="handleRejectTool"
      />

      <!-- Agent 向用户提问悬浮卡片 -->
      <AgentUserQuestion
        :question="pendingQuestion"
        @submit="resolveQuestion"
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
      @model-change="handleModelChange"
      @knowledge-change="handleKnowledgeChange"
      @tools-change="handleToolsChange"
    />

  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { Icon } from '@/components/Icon';
import { message } from 'ant-design-vue';
import logger from '@/utils/logger';
import { useAgentChat } from './hooks/useAgentChat';
import { useBrandConfig } from './hooks/useBrandConfig';
import { useConversationList } from './hooks/useConversationList';
import { getAvailableModels, getKnowledgeList, updateConversationAgent } from './agent.api';
import { getMcpTools } from './agent.api.adapted';
import ChatConfigDrawer from './components/ChatConfigDrawer.vue';
import ChatHeader from './components/ChatHeader.vue';
import ChatInput from './components/ChatInput.vue';
import ChatMessages from './components/ChatMessages.vue';
import AgentSlide from './components/AgentSlide.vue';
import AgentUserQuestion from './components/AgentUserQuestion.vue';
import AgentSelector from './components/AgentSelector.vue';
import { AGENT_CONFIG_STORAGE_KEY } from './agent.constants';
import type { ModelInfo, KnowledgeInfo } from './agent.types';
import { ModelType } from '@/types/model.types';
import type { BrandConfig } from './hooks/useBrandConfig';

declare global {
  interface Window {
    __ZENO_AGENT_BRAND__?: Partial<BrandConfig>;
  }
}

const { t } = useI18n();
const router = useRouter();
const route = useRoute();

type BrandLink = {
  label: string;
  url: string;
};
const { brandConfig, brandStyle, showBrandTitle, brandLinks, brandVersion, loadBrandConfig } = useBrandConfig();

// 配置抽屉
const showConfigDrawer = ref(false);

// Agent 选择器
const selectedAgentId = ref<string | undefined>(undefined);

// 会话 ID（由会话列表与SSE更新）
const currentConversationId = ref('');

// Agent 配置
const selectedModelId = ref('');
const selectedKnowledgeIds = ref<string[]>([]);
const selectedTools = ref<string[]>([]);
const executionMode = ref<'AUTO' | 'MANUAL'>('AUTO');
const isConfigInitialized = ref(false);

type AgentConfigCache = {
  modelId: string;
  knowledgeIds: string[];
  enabledTools: string[];
  mode: 'AUTO' | 'MANUAL';
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
  pendingQuestion,
  resolveQuestion,
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
    return t('common.loading');
  }
  return t('agent.inputPlaceholder');
});

const capabilityItems = computed(() => [
  {
    icon: '📚',
    title: t('home.capabilities.rag.title'),
    desc: t('home.capabilities.rag.desc'),
  },
  {
    icon: '🧰',
    title: t('home.capabilities.mcp.title'),
    desc: t('home.capabilities.mcp.desc'),
  },
  {
    icon: '⚡',
    title: t('home.capabilities.context.title'),
    desc: t('home.capabilities.context.desc'),
  },
  {
    icon: '🧠',
    title: t('home.capabilities.agent.title'),
    desc: t('home.capabilities.agent.desc'),
  },
]);

const scenarioPrompts = computed(() => [
  t('agent.scenarios.deviceCheck'),
  t('agent.scenarios.compliance'),
  t('agent.scenarios.assetQuery'),
  t('agent.scenarios.riskCheck'),
]);

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
    updatedAt: Date.now(),
  };
  localStorage.setItem(AGENT_CONFIG_STORAGE_KEY, JSON.stringify(payload));
};

const applyCachedConfig = (cache: AgentConfigCache) => {
  selectedModelId.value = cache.modelId || '';
  selectedKnowledgeIds.value = [...cache.knowledgeIds];
  selectedTools.value = [...cache.enabledTools];
  executionMode.value = cache.mode || 'AUTO';
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
    agentId: selectedAgentId.value,
    modelId: selectedModelId.value,
    knowledgeIds: selectedKnowledgeIds.value,
    enabledTools: selectedTools.value,
    mode: executionMode.value,
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
  // // 状态已经通过 v-model 双向绑定了，这里只需要处理额外的逻辑
  // if (model) {
  //   message.success(`已切换到模型: ${model.displayName}`);
  // }
};

const handleKnowledgeChange = (knowledgeIds: string[], knowledgeList: KnowledgeInfo[]) => {
  logger.debug('知识库变更:', knowledgeIds, knowledgeList);
};

const handleToolsChange = (tools: string[]) => {
  logger.debug('工具变更:', tools);
};

// Agent 选择器处理
const handleAgentChange = (agentId: string | undefined) => {
  selectedAgentId.value = agentId;
  // 若当前已有真实会话，更新会话绑定
  const convId = currentConversationId.value;
  if (convId && !convId.startsWith('temp-')) {
    updateConversationAgent(convId, agentId || null).catch((err) => {
      logger.warn('更新会话 agentId 失败:', err);
    });
  }
};

// 切换会话时同步 selectedAgentId（从会话列表中读取绑定信息）
watch(currentConversationId, (newId) => {
  if (!newId) return;
  const conv = conversations.value.find((c) => c.id === newId);
  if (conv) {
    selectedAgentId.value = conv.agentId || undefined;
  }
});

// 初始化
onMounted(() => {
  loadBrandConfig();
  initAgentConfig();
  loadConversations();
  // 若从 Agent 管理页跳转而来，自动选中对应的 Agent
  const queryAgentId = route.query.agentId as string | undefined;
  if (queryAgentId) {
    selectedAgentId.value = queryAgentId;
  }
});

watch(
  [selectedModelId, selectedKnowledgeIds, selectedTools, executionMode],
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
  background: transparent;
  overflow: hidden;
  position: relative; /* Added relative positioning for absolute child */
  --brand-primary: var(--google-blue);
}

.left-slide {
    width: 280px;
    height: 100%;
    background: rgba(15, 23, 42, 0.4); /* Glass effect */
    border-right: 1px solid rgba(59, 130, 246, 0.1);
    backdrop-filter: blur(12px);
    transition: all 0.3s cubic-bezier(0.4, 0.0, 0.2, 1);
    position: relative;

    &.collapsed {
      width: 0;
      overflow: hidden;
      border-right: none;
    }
  }

  .slide-toggle-btn {
    position: absolute;
    top: 50%;
    left: 280px; /* Default expanded position */
    transform: translateY(-50%);
    width: 24px;
    height: 48px;
    background: rgba(15, 23, 42, 0.8);
    border: 1px solid rgba(59, 130, 246, 0.2);
    border-radius: 0 8px 8px 0;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    z-index: 10;
    transition: all 0.3s cubic-bezier(0.4, 0.0, 0.2, 1);
    box-shadow: 2px 0 4px rgba(0,0,0,0.2);
    color: var(--color-text-secondary);

    &:hover {
      background: rgba(59, 130, 246, 0.2);
      color: var(--color-text-primary);
    }
    
    &.collapsed {
      left: 0;
      border-radius: 0 8px 8px 0; /* Maintain border radius */
      border-left: none; /* Optional: might look better */
    }
  }

.right-chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  overflow: hidden;
  background: transparent;
  position: relative;

  &.expanded {
    margin-left: 0;
  }
}

.agent-selector-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  border-bottom: 1px solid rgba(59, 130, 246, 0.1);
  background: rgba(15, 23, 42, 0.3);
  flex-shrink: 0;
}

.agent-selector-label {
  font-size: 12px;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
  flex-shrink: 0;
}
</style>

