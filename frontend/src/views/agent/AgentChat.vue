<template>
  <div class="agent-chat-container" :style="brandStyle">
    <!-- 左侧会话列表 -->
    <div v-if="showSlide" class="left-slide" :class="{ collapsed: slideCollapsed }">
      <AgentSlide
        :conversations="conversations"
        :active-id="currentConversationId"
        :agent-name-map="agentNameMap"
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

      <!-- Agent 信息横幅 -->
      <div class="agent-banner">
        <div class="agent-banner-inner">
          <div class="agent-banner-avatar">
            <Icon icon="ant-design:robot-outlined" class="agent-banner-icon" />
          </div>
          <div class="agent-banner-info">
            <div class="agent-banner-name">
              <span v-if="currentAgent" class="agent-name-text">{{ currentAgent.name }}</span>
              <span v-else class="agent-name-placeholder">未选择 Agent</span>
              <span v-if="currentAgent?.builtin" class="agent-builtin-badge">系统</span>
            </div>
            <div v-if="currentAgent?.description" class="agent-banner-desc">{{ currentAgent.description }}</div>
            <div v-else class="agent-banner-desc agent-banner-desc--empty">点击右侧切换 Agent 以开始专属对话</div>
          </div>
          <div class="agent-banner-selector">
            <AgentSelector
              ref="agentSelectorRef"
              v-model="selectedAgentId"
              @change="handleAgentChange"
              @agents-loaded="handleAgentsLoaded"
            />
          </div>
        </div>
      </div>

      <ChatMessages
        ref="chatMessagesRef"
        :messages="messages"
        :welcome-categories="welcomeCategories"
        :agent-description="currentAgent?.description"
        @apply-prompt="applyScenarioPrompt"
        @confirm-tool="handleConfirmTool"
        @reject-tool="handleRejectTool"
        @answer-question="resolveQuestion"
      />

      <!-- PERSONAL MCP 行内密钥补充卡片 -->
      <div v-if="pendingSecretRequest" class="secret-card-wrap">
        <McpSecretInlineCard
          :request="pendingSecretRequest"
          @execute="resolveSecretRequest"
          @skip="skipSecretRequest"
        />
      </div>

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
      @tools-meta-change="handleToolsMetaChange"
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
import { getAvailableModels, getKnowledgeList, updateConversationAgent, getMcpServers, getMcpServerTools } from './agent.api';
import type { McpServerInfo } from './agent.types';
import { buildAgentToolsConfig } from './agent.helpers';
import type { ToolsMetaMap } from './agent.helpers';
import ChatConfigDrawer from './components/ChatConfigDrawer.vue';
import ChatHeader from './components/ChatHeader.vue';
import ChatInput from './components/ChatInput.vue';
import ChatMessages from './components/ChatMessages.vue';
import AgentSlide from './components/AgentSlide.vue';
import AgentSelector from './components/AgentSelector.vue';
import McpSecretInlineCard from './components/McpSecretInlineCard.vue';
import { AGENT_CONFIG_STORAGE_KEY } from './agent.constants';
import type { ModelInfo, KnowledgeInfo, AgentDefinition } from './agent.types';
import { ModelType } from '@/types/model.types';
import type { BrandConfig } from './hooks/useBrandConfig';

declare global {
  interface Window {
    __ZENO_AGENT_BRAND__?: Partial<BrandConfig>;
  }
}

const { t, tm, rt } = useI18n();
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
const agentSelectorRef = ref<InstanceType<typeof AgentSelector> | null>(null);
// 标记 URL 携带的 agentId 是否已生效，防止 watch(currentConversationId) 在首次加载时将其覆盖
const urlAgentIdHandled = ref(false);

/** 当前选中的 Agent 定义（从 AgentSelector 内部列表查询） */
const currentAgent = computed<AgentDefinition | undefined>(() => {
  if (!selectedAgentId.value || !agentSelectorRef.value) return undefined;
  return agentSelectorRef.value.getAgentById(selectedAgentId.value);
});

/** agentId → agentName 映射，供会话列表展示 Agent 名称 */
const agentNameMap = computed<Record<string, string>>(() => {
  if (!agentSelectorRef.value?.agents) return {};
  const map: Record<string, string> = {};
  for (const agent of agentSelectorRef.value.agents) {
    map[agent.id] = agent.name;
  }
  return map;
});

// 会话 ID（由会话列表与SSE更新）
const currentConversationId = ref('');

// Agent 配置
const selectedModelId = ref('');
const selectedKnowledgeIds = ref<string[]>([]);
const selectedTools = ref<string[]>([]);
const selectedServerMcpIds = ref<string[]>([]);
const executionMode = ref<'AUTO' | 'MANUAL'>('AUTO');
const isConfigInitialized = ref(false);
// 用户是否手动修改过工具选择（true 时切换 Agent 不覆盖工具配置）
const userHasCustomizedTools = ref(false);
// 工具元数据：工具名 → serverId（系统工具 = '__system_tools__'），由 AgentToolConfig emit
const toolsMetaMap = ref<ToolsMetaMap>({});

// PERSONAL MCP 服务器缓存（serverId → McpServerInfo）
const personalMcpServerMap = ref<Map<string, McpServerInfo>>(new Map());

/** 加载所有 PERSONAL MCP 服务器，并建立 id → info 缓存 */
const loadPersonalMcpServers = async () => {
  try {
    const servers = await getMcpServers(1); // scope=1 表示 PERSONAL
    const map = new Map<string, McpServerInfo>();
    for (const s of servers) {
      map.set(s.id, s);
    }
    personalMcpServerMap.value = map;
    logger.debug('[AgentChat] 已加载 PERSONAL MCP 服务器:', map.size);
  } catch (err) {
    logger.warn('[AgentChat] 加载 PERSONAL MCP 服务器失败:', err);
  }
};

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
  resolveQuestion,
  pendingSecretRequest,
  resolveSecretRequest,
  skipSecretRequest,
} = useAgentChat({
  conversationId: currentConversationId,
  defaultModelId: selectedModelId.value,
  defaultKnowledgeIds: selectedKnowledgeIds.value,
  getAgentName: (agentId: string) => agentSelectorRef.value?.getAgentById(agentId)?.name,
  getPersonalMcpServer: (serverId: string) => personalMcpServerMap.value.get(serverId),
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

const welcomeCategories = computed(() => {
  const toStrings = (key: string): string[] => {
    const raw = tm(key);
    if (Array.isArray(raw)) return raw.map((v) => rt(v));
    return [];
  };
  return [
    {
      label: t('agent.welcomeCategories.knowledge.label'),
      items: toStrings('agent.welcomeCategories.knowledge.items'),
    },
    {
      label: t('agent.welcomeCategories.tool.label'),
      items: toStrings('agent.welcomeCategories.tool.items'),
    },
    {
      label: t('agent.welcomeCategories.analysis.label'),
      items: toStrings('agent.welcomeCategories.analysis.items'),
    },
  ];
});

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
  const [models, knowledgeList, servers] = await Promise.all([
    getAvailableModels(ModelType.CHAT).catch(() => []),
    getKnowledgeList().catch(() => []),
    getMcpServers().catch(() => []),
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

  if (servers.length > 0 && selectedTools.value.length > 0) {
    const globalServers = servers.filter((s) => s.scope === 0);
    const personalServers = servers.filter((s) => s.scope === 1);

    // 并发拉取所有 GLOBAL 服务器工具名
    const toolResults = await Promise.allSettled(
      globalServers.map((s) => getMcpServerTools(s.id).catch(() => []))
    );
    const globalToolNames = new Set(
      toolResults.flatMap((r) => (r.status === 'fulfilled' ? r.value.map((t) => t.name) : []))
    );

    // 若存在 PERSONAL 服务器，无法在前端枚举其工具名，保守不删任何工具名。
    // 仅当没有 PERSONAL 服务器、且 GLOBAL 工具集非空时才做清理，
    // 避免删掉用户选择的有效工具名。
    if (personalServers.length === 0 && globalToolNames.size > 0) {
      selectedTools.value = selectedTools.value.filter((name) => globalToolNames.has(name));
    }
  }
};

/**
 * 将 selectedTools（工具名数组）+ toolsMetaMap 转换为后端所需参数格式。
 * 返回 { mcpServers, systemTools }，供 sendMessage 使用。
 */
const buildToolsRequest = () => {
  return buildAgentToolsConfig(selectedTools.value, toolsMetaMap.value);
};

/**
 * 根据 Agent 定义的 mcpServers + systemTools 并发拉取工具，作为对话级默认工具列表。
 * 仅在用户未手动修改工具时触发。
 */
const applyAgentDefaultTools = async (agentId: string | undefined) => {
  if (userHasCustomizedTools.value) return;

  const agentDef = agentId ? agentSelectorRef.value?.getAgentById(agentId) : undefined;
  const mcpSelections = agentDef?.tools?.mcpServers ?? [];
  const agentSystemTools = agentDef?.tools?.systemTools ?? [];

  if (mcpSelections.length === 0 && agentSystemTools.length === 0) {
    selectedTools.value = [];
    toolsMetaMap.value = {};
    return;
  }

  try {
    // 并发拉取 MCP 工具
    const mcpResults = await Promise.allSettled(
      mcpSelections.map((sel) =>
        getMcpServerTools(sel.serverId).then((tools) =>
          sel.toolNames && sel.toolNames.length > 0
            ? tools.filter((t) => sel.toolNames!.includes(t.name))
            : tools
        ).catch(() => [])
      )
    );
    const mcpToolNames = mcpResults.flatMap((r) =>
      r.status === 'fulfilled' ? r.value.map((t: { name: string }) => t.name) : []
    );

    // 所有工具名汇总
    const allToolNames = [...agentSystemTools, ...mcpToolNames];
    selectedTools.value = allToolNames;

    // 构建元数据映射
    const meta: ToolsMetaMap = {};
    for (const name of agentSystemTools) {
      meta[name] = SYSTEM_TOOLS_SERVER_ID;
    }
    // MCP 工具按 sel 分组映射
    mcpSelections.forEach((sel, idx) => {
      if (mcpResults[idx].status === 'fulfilled') {
        const tools = (mcpResults[idx] as PromiseFulfilledResult<{ name: string }[]>).value;
        for (const t of tools) {
          meta[t.name] = sel.serverId;
        }
      }
    });
    toolsMetaMap.value = meta;

    logger.debug('[AgentChat] Agent 默认工具已加载:', allToolNames.length, '个');
  } catch (err) {
    logger.warn('[AgentChat] 加载 Agent 默认工具失败:', err);
    selectedTools.value = [];
    toolsMetaMap.value = {};
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
  
  // 将 selectedTools 转换为后端所需的 mcpServers + systemTools 参数
  const { mcpServers: reqMcpServers, systemTools: reqSystemTools } = buildToolsRequest();
  await sendMessage(content, {
    agentId: selectedAgentId.value,
    modelId: selectedModelId.value,
    knowledgeIds: selectedKnowledgeIds.value,
    mcpServers: reqMcpServers,
    systemTools: reqSystemTools,
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
  // 用户主动修改工具，标记为手动配置，后续切换 Agent 不再覆盖
  userHasCustomizedTools.value = true;
};

const handleToolsMetaChange = (meta: ToolsMetaMap) => {
  toolsMetaMap.value = meta;
};

// Agent 选择器处理
const handleAgentChange = (agentId: string | undefined) => {
  selectedAgentId.value = agentId;
  // 切换 Agent 时重置手动修改标记，并加载新 Agent 的默认工具
  userHasCustomizedTools.value = false;
  applyAgentDefaultTools(agentId);
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
  // 切换会话时重置手动修改标记，让新会话恢复 Agent 默认工具
  userHasCustomizedTools.value = false;
  // 如果 URL 携带了 agentId 且尚未生效，优先使用 URL 参数，不跟随会话覆盖
  const queryAgentId = route.query.agentId as string | undefined;
  if (queryAgentId && !urlAgentIdHandled.value) {
    urlAgentIdHandled.value = true;
    selectedAgentId.value = queryAgentId;
    applyAgentDefaultTools(queryAgentId);
    return;
  }
  const conv = conversations.value.find((c) => c.id === newId);
  if (conv) {
    const newAgentId = conv.agentId || undefined;
    selectedAgentId.value = newAgentId;
    applyAgentDefaultTools(newAgentId);
  }
});

// 初始化
onMounted(() => {
  loadBrandConfig();
  initAgentConfig();
  loadConversations();
  loadPersonalMcpServers();
});

// AgentSelector 完成 agents 加载后的回调
// agents 列表就绪后，确保当前 selectedAgentId 在下拉框中正确显示
const handleAgentsLoaded = (_loadedAgents: import('./agent.types').AgentDefinition[]) => {
  // AgentSelector 内部已在 loadAgents 完成后重新同步 props.modelValue，此处无需额外处理
};

watch(
  [selectedModelId, selectedKnowledgeIds, selectedTools, executionMode],
  () => {
    persistConfigCache();
  },
  { deep: true }
);

</script>

<style scoped lang="less">
// ── 响应式内容宽度 CSS 变量系统 ──────────────────────────────────────────────
// 通过在根容器设置 --content-max-width，各子组件统一引用实现自适应
.agent-chat-container {
  display: flex;
  height: 100%;
  background: transparent;
  overflow: hidden;
  position: relative;
  --brand-primary: var(--google-blue);

  // 默认（手机/小屏）：内容全宽，最小水平 padding
  --content-max-width: 100%;
  --content-padding-x: 12px;
  --content-padding-bottom: 16px;

  // 平板及以上：开始限制宽度
  @media (min-width: 768px) {
    --content-padding-x: 24px;
    --content-padding-bottom: 20px;
  }

  // 普通桌面（1280px+）：内容列宽 900px 居中
  @media (min-width: 1280px) {
    --content-max-width: 900px;
    --content-padding-x: 40px;
    --content-padding-bottom: 24px;
  }

  // 宽屏（1600px+）：内容列宽适度扩展
  @media (min-width: 1600px) {
    --content-max-width: 1080px;
    --content-padding-x: 60px;
    --content-padding-bottom: 28px;
  }

  // 超宽屏（1920px+）
  @media (min-width: 1920px) {
    --content-max-width: 1200px;
    --content-padding-x: 80px;
    --content-padding-bottom: 32px;
  }
}

.left-slide {
  width: 280px;
  height: 100%;
  background: rgba(15, 23, 42, 0.4);
  border-right: 1px solid rgba(59, 130, 246, 0.1);
  backdrop-filter: blur(12px);
  transition: all 0.3s cubic-bezier(0.4, 0.0, 0.2, 1);
  position: relative;
  flex-shrink: 0;

  // 手机端隐藏侧边栏，默认折叠
  @media (max-width: 767px) {
    position: absolute;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: 20;
    width: 260px;
  }

  &.collapsed {
    width: 0;
    overflow: hidden;
    border-right: none;
  }
}

.slide-toggle-btn {
  position: absolute;
  top: 50%;
  left: 280px;
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

  @media (max-width: 767px) {
    left: 260px;
  }

  &:hover {
    background: rgba(59, 130, 246, 0.2);
    color: var(--color-text-primary);
  }

  &.collapsed {
    left: 0;
    border-radius: 0 8px 8px 0;
    border-left: none;
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

.secret-card-wrap {
  padding: 0 var(--content-padding-x) 8px;
  flex-shrink: 0;

  // 内容列宽对齐
  > * {
    max-width: var(--content-max-width);
    margin-left: auto;
    margin-right: auto;
  }
}

.agent-banner {
  display: flex;
  align-items: center;
  border-bottom: 1px solid rgba(59, 130, 246, 0.15);
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(8px);
  flex-shrink: 0;
  min-height: 56px;
  padding: 10px var(--content-padding-x);

  // 内部内容通过 inner 容器居中对齐到内容列宽
  .agent-banner-inner {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    max-width: var(--content-max-width);
    margin-left: auto;
    margin-right: auto;
  }

  .agent-banner-avatar {
    width: 36px;
    height: 36px;
    border-radius: 8px;
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.3), rgba(139, 92, 246, 0.3));
    border: 1px solid rgba(59, 130, 246, 0.3);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    .agent-banner-icon {
      font-size: 18px;
      color: #60a5fa;
    }
  }

  .agent-banner-info {
    flex: 1;
    min-width: 0;
    overflow: hidden;

    .agent-banner-name {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 2px;

      .agent-name-text {
        font-size: 14px;
        font-weight: 600;
        color: #e2e8f0;
        font-family: 'JetBrains Mono', monospace;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .agent-name-placeholder {
        font-size: 14px;
        font-weight: 500;
        color: #475569;
        font-family: 'JetBrains Mono', monospace;
      }

      .agent-builtin-badge {
        font-size: 10px;
        padding: 1px 5px;
        background: rgba(59, 130, 246, 0.2);
        color: #60a5fa;
        border: 1px solid rgba(59, 130, 246, 0.3);
        border-radius: 3px;
        line-height: 16px;
        flex-shrink: 0;
      }
    }

    .agent-banner-desc {
      font-size: 11px;
      color: #64748b;
      font-family: 'JetBrains Mono', monospace;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;

      &.agent-banner-desc--empty {
        color: #334155;
        font-style: italic;
      }
    }
  }

  .agent-banner-selector {
    flex-shrink: 0;
  }
}
</style>

