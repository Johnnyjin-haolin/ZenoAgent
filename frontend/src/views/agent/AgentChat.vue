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
      <!-- 顶部配置区 -->
      <div class="chat-header">
        <div class="header-title">
          <Icon icon="ant-design:robot-outlined" class="title-icon" />
          <img v-if="brandConfig.logo" :src="brandConfig.logo" class="brand-logo" alt="brand" />
          <h2 v-if="showBrandTitle">{{ brandConfig.name }}</h2>
        </div>
        
        <div class="header-actions">
          <a-button size="small" @click="handleNavigateToKnowledgeBases" style="margin-right: 8px;">
            <template #icon>
              <Icon icon="ant-design:book-outlined" />
            </template>
            知识库管理
          </a-button>
          <a-button size="small" @click="showConfigDrawer = true">
            <template #icon>
              <Icon icon="ant-design:setting-outlined" />
            </template>
            配置
          </a-button>
          <a-dropdown v-if="brandLinks.length > 0" placement="bottomRight">
            <a-button size="small" type="default" class="header-help-button">
              <template #icon>
                <Icon icon="ant-design:question-circle-outlined" />
              </template>
              帮助
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item v-for="link in brandLinks" :key="link.label">
                  <a :href="link.url" target="_blank" rel="noopener noreferrer">{{ link.label }}</a>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-tag v-if="brandVersion" class="header-version-tag">{{ brandVersion }}</a-tag>
        </div>
      </div>

      <!-- 消息列表区 -->
      <div ref="chatScrollRef" class="chat-messages">
        <template v-if="messages.length > 0">
          <AgentMessage
            v-for="msg in messages"
            :key="msg.id"
            :message="msg"
            @confirm-tool="handleConfirmTool"
            @reject-tool="handleRejectTool"
          />
        </template>
        
        <!-- 欢迎消息 -->
        <div v-else class="welcome-message">
          <div class="welcome-icon">🤖</div>
          <h3>欢迎使用 ZenoAgent</h3>
          <p class="welcome-subtitle">企业级 AI Agent，支持 RAG / MCP / 多模型 / 流式过程</p>
          <div class="welcome-capabilities">
            <div v-for="item in capabilityItems" :key="item.title" class="capability-card">
              <div class="capability-icon">{{ item.icon }}</div>
              <div class="capability-title">{{ item.title }}</div>
              <div class="capability-desc">{{ item.desc }}</div>
            </div>
          </div>
          <div class="welcome-scenarios">
            <div class="section-title">场景示例</div>
            <div class="scenario-tags">
              <a-tag
                v-for="prompt in scenarioPrompts"
                :key="prompt"
                class="scenario-tag"
                @click="applyScenarioPrompt(prompt)"
              >
                {{ prompt }}
              </a-tag>
            </div>
          </div>
          <p class="welcome-hint">选择模型 → 选择知识库 → 输入问题开始对话</p>
        </div>
      </div>

      <!-- 底部输入区 -->
      <div class="chat-footer">
        <!-- 当前状态提示 -->
        <div v-if="currentStatus" class="status-bar">
          <a-spin size="small" />
          <span>{{ currentStatus }}</span>
        </div>

        <!-- 输入框区域（参考豆包设计） -->
        <div class="input-container">
          <div class="input-wrapper">
            <!-- 输入框 -->
            <a-textarea
              ref="inputRef"
              v-model:value="userInput"
              :placeholder="inputPlaceholder"
              :rows="4"
              :disabled="loading"
              :bordered="false"
              @pressEnter="handlePressEnter"
            />
            
            <!-- 左下角：模型选择器 -->
            <div class="input-bottom-left">
              <AgentModelSelector
                v-model="selectedModelId"
                :compact="true"
                placeholder="选择模型"
                @change="handleModelChange"
              />
            </div>
            
            <!-- 右下角：发送按钮 -->
            <div class="input-bottom-right">
              <a-button
                v-if="loading"
                type="text"
                danger
                size="small"
                @click="handleStop"
                title="停止生成"
                class="action-button"
              >
                <template #icon>
                  <Icon icon="ant-design:stop-outlined" />
                </template>
              </a-button>
              <a-button
                v-else
                type="text"
                :disabled="!userInput.trim()"
                @click="handleSend"
                title="发送消息"
                class="action-button send-button"
              >
                <template #icon>
                  <Icon icon="ant-design:send-outlined" />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>

    </div>

    <!-- 配置抽屉 -->
    <a-drawer
      v-model:open="showConfigDrawer"
      title="Agent 配置"
      :width="400"
      placement="right"
    >
      <div class="config-content">
        <!-- 模型选择 -->
        <div class="config-item">
          <AgentModelSelector
            v-model="selectedModelId"
            @change="handleModelChange"
          />
        </div>

        <!-- 知识库选择 -->
        <div class="config-item">
          <AgentKnowledgeSelector
            v-model="selectedKnowledgeIds"
            @change="handleKnowledgeChange"
          />
        </div>

        <!-- 工具配置 -->
        <div class="config-item">
          <AgentToolConfig
            v-model="selectedTools"
            @change="handleToolsChange"
          />
        </div>

        <!-- 执行模式 -->
        <div class="config-item">
          <div class="config-label">
            <Icon icon="ant-design:control-outlined" />
            <span>执行模式</span>
          </div>
          <a-radio-group v-model:value="executionMode">
            <a-radio value="AUTO">自动模式</a-radio>
            <a-radio value="MANUAL">手动模式</a-radio>
          </a-radio-group>
          <div class="config-hint">
            自动模式：AI 自主决策工具调用<br />
            手动模式：需要确认后执行工具
          </div>
        </div>
      </div>
    </a-drawer>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { Icon } from '@/components/Icon';
import { message } from 'ant-design-vue';
import { useAgentChat } from './hooks/useAgentChat';
import { getAvailableModels, getConversations, getKnowledgeList, updateConversationTitle } from './agent.api';
import { getMcpTools } from './agent.api.adapted';
import AgentMessage from './components/AgentMessage.vue';
import AgentSlide from './components/AgentSlide.vue';
import AgentModelSelector from './components/AgentModelSelector.vue';
import AgentKnowledgeSelector from './components/AgentKnowledgeSelector.vue';
import AgentToolConfig from './components/AgentToolConfig.vue';
import type { ConversationInfo, ModelInfo, KnowledgeInfo } from './agent.types';
import { ModelType } from '@/types/model.types';

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

type BrandConfig = {
  name: string;
  logo?: string;
  primaryColor?: string;
  links?: BrandLink[];
  version?: string;
  showFooter?: boolean;
  showTitle?: boolean;
  embedMode?: boolean;
};

const defaultBrandConfig: BrandConfig = {
  name: 'ZenoAgent',
  primaryColor: '#1890ff',
  showFooter: true,
  showTitle: true,
  embedMode: false,
  links: [],
};

const normalizeBrandConfig = (config?: Partial<BrandConfig>): BrandConfig => {
  const merged = {
    ...defaultBrandConfig,
    ...(config || {}),
  };
  return {
    ...merged,
    links: Array.isArray(merged.links) ? merged.links : defaultBrandConfig.links,
  };
};

const resolveBrandConfig = (fileConfig?: Partial<BrandConfig>): BrandConfig => {
  const windowConfig = window.__ZENO_AGENT_BRAND__ || {};
  return normalizeBrandConfig({
    ...fileConfig,
    ...windowConfig,
  });
};

const brandConfig = ref<BrandConfig>(normalizeBrandConfig());
const brandStyle = computed(() => ({
  '--brand-primary': brandConfig.value.primaryColor || defaultBrandConfig.primaryColor,
}));
const showBrandTitle = computed(() => {
  if (typeof brandConfig.value.showTitle === 'boolean') {
    return brandConfig.value.showTitle;
  }
  return defaultBrandConfig.showTitle;
});
const brandLinks = computed(() => brandConfig.value.links || []);
const brandVersion = computed(() => brandConfig.value.version || '');

const loadBrandConfig = async () => {
  try {
    const response = await fetch('/brand.json', { cache: 'no-store' });
    if (!response.ok) {
      brandConfig.value = resolveBrandConfig();
      return;
    }
    const fileConfig = await response.json();
    brandConfig.value = resolveBrandConfig(fileConfig);
  } catch (error) {
    console.warn('加载品牌配置失败:', error);
    brandConfig.value = resolveBrandConfig();
  }
};

// 会话管理
type ConversationView = ConversationInfo & { isTemporary?: boolean };
const conversations = ref<ConversationView[]>([]);
const currentConversationId = ref('');
const showSlide = ref(true);
const slideCollapsed = ref(false);
const temporaryConversationId = ref<string | null>(null);
const temporaryHasMessages = ref(false);

// 配置抽屉
const showConfigDrawer = ref(false);

// 导航到知识库管理页面
const handleNavigateToKnowledgeBases = () => {
  router.push('/knowledge-bases');
};

// Agent 配置
const selectedModelId = ref('');
const selectedKnowledgeIds = ref<string[]>([]);
const selectedTools = ref<string[]>([]);
const executionMode = ref<'AUTO' | 'MANUAL'>('AUTO');
const isConfigInitialized = ref(false);

const AGENT_CONFIG_STORAGE_KEY = 'agent.chat.config.v1';

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
} = useAgentChat({
  conversationId: currentConversationId,  // Ref 会自动响应
  defaultModelId: selectedModelId.value,  // 初始值
  defaultKnowledgeIds: selectedKnowledgeIds.value,  // 初始值
  defaultEnabledTools: selectedTools.value,  // 初始值
});

// 用户输入
const userInput = ref('');
const inputRef = ref();
const chatScrollRef = ref();

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
    if (inputRef.value) {
      inputRef.value.focus();
    }
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
    console.warn('读取Agent配置缓存失败:', error);
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

// 加载会话列表
const loadConversations = async () => {
  try {
    const result = await getConversations();
    conversations.value = result;
    
    // 如果有会话，选择第一个并加载其历史消息
    if (result.length > 0 && !currentConversationId.value) {
      currentConversationId.value = result[0].id;
      await loadMessages(result[0].id);
      await scrollToBottom();
    }
  } catch (error) {
    console.error('加载会话列表失败:', error);
  }
};

// 选择会话
const handleSelectConversation = async (conversation: ConversationInfo) => {
  // 选择其他会话前，清理未发送消息的临时会话
  if (temporaryConversationId.value === currentConversationId.value && !temporaryHasMessages.value) {
    const index = conversations.value.findIndex((c) => c.id === temporaryConversationId.value);
    if (index !== -1) {
      conversations.value.splice(index, 1);
    }
    temporaryConversationId.value = null;
  }

  currentConversationId.value = conversation.id;
  clearMessages();
  
  // 加载历史消息
  await loadMessages(conversation.id);
  
  // 滚动到底部
  await scrollToBottom();
};

// 新建会话
const handleNewConversation = () => {
  // 若已有临时会话且已开始对话，直接切换到该会话
  if (temporaryConversationId.value && temporaryHasMessages.value) {
    currentConversationId.value = temporaryConversationId.value;
    userInput.value = '';
    return;
  }

  // 如果已有未发送消息的临时会话，先移除
  if (temporaryConversationId.value && !temporaryHasMessages.value) {
    const index = conversations.value.findIndex((c) => c.id === temporaryConversationId.value);
    if (index !== -1) {
      conversations.value.splice(index, 1);
    }
  }

  const tempId = `temp-${Date.now()}`;
  const tempConversation: ConversationView = {
    id: tempId,
    title: '新对话',
    isEdit: false,
    disabled: true,
    isTemporary: true,
  };
  conversations.value.unshift(tempConversation);
  temporaryConversationId.value = tempId;
  temporaryHasMessages.value = false;

  currentConversationId.value = tempId;
  clearMessages();
  userInput.value = '';
  message.success('已创建新对话');
};

// 更新会话
const handleUpdateConversation = async (conversation: ConversationInfo) => {
  try {
    const success = await updateConversationTitle(conversation.id, conversation.title);
    if (success) {
      message.success('更新成功');
      // 更新本地列表
      const index = conversations.value.findIndex((c) => c.id === conversation.id);
      if (index !== -1) {
        conversations.value[index].title = conversation.title;
      }
    } else {
      message.error('更新失败');
    }
  } catch (error) {
    console.error('更新会话失败:', error);
    message.error('更新失败');
  }
};

// 删除会话
const handleDeleteConversation = (conversationId: string) => {
  const index = conversations.value.findIndex((c) => c.id === conversationId);
  if (index !== -1) {
    conversations.value.splice(index, 1);
  }
  
  // 如果删除的是当前会话，创建新会话
  if (currentConversationId.value === conversationId) {
    handleNewConversation();
  }
};

// 折叠/展开侧边栏
const toggleSlide = () => {
  slideCollapsed.value = !slideCollapsed.value;
};

// 发送消息
const handleSend = async () => {
  const content = userInput.value.trim();
  if (!content) {
    return;
  }
  if (temporaryConversationId.value === currentConversationId.value) {
    temporaryHasMessages.value = true;
  }

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
  });
  
  // 滚动到底部
  await scrollToBottom();
  
  // 确保输入框获得焦点（如果存在）
  if (inputRef.value) {
    await nextTick();
    inputRef.value.focus();
  }
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

// 处理回车
const handlePressEnter = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
};

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick();
  if (chatScrollRef.value) {
    chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight;
  }
};

// 配置变更处理
const handleModelChange = (modelId: string, model: ModelInfo | null) => {
  console.log('模型变更:', modelId, model);
  // 状态已经通过 v-model 双向绑定了，这里只需要处理额外的逻辑
  if (model) {
    message.success(`已切换到模型: ${model.displayName}`);
  }
};

const handleKnowledgeChange = (knowledgeIds: string[], knowledgeList: KnowledgeInfo[]) => {
  console.log('知识库变更:', knowledgeIds, knowledgeList);
};

const handleToolsChange = (tools: string[]) => {
  console.log('工具变更:', tools);
};

// 初始化
onMounted(() => {
  loadBrandConfig();
  initAgentConfig();
  loadConversations();
});

watch(
  [selectedModelId, selectedKnowledgeIds, selectedTools, executionMode],
  () => {
    persistConfigCache();
  },
  { deep: true }
);

const syncConversationTitle = async (conversationId: string) => {
  try {
    const result = await getConversations();
    const matched = result.find((item) => item.id === conversationId);
    if (matched) {
      const index = conversations.value.findIndex((c) => c.id === conversationId);
      if (index !== -1) {
        conversations.value[index].title = matched.title;
        conversations.value[index].modelId = matched.modelId;
        conversations.value[index].modelName = matched.modelName;
        conversations.value[index].messageCount = matched.messageCount;
      }
    }
  } catch (error) {
    console.error('同步会话标题失败:', error);
  }
};

// 临时会话在服务端生成 ID 后，替换成正式会话
watch(currentConversationId, async (newId, oldId) => {
  if (!newId || !oldId) return;
  if (oldId !== temporaryConversationId.value) return;

  const existingIndex = conversations.value.findIndex((c) => c.id === newId);
  const tempIndex = conversations.value.findIndex((c) => c.id === oldId);

  if (existingIndex !== -1) {
    if (tempIndex !== -1) {
      conversations.value.splice(tempIndex, 1);
    }
  } else if (tempIndex !== -1) {
    conversations.value[tempIndex].id = newId;
    conversations.value[tempIndex].isTemporary = false;
    conversations.value[tempIndex].disabled = false;
  }

  await syncConversationTitle(newId);

  temporaryConversationId.value = null;
  temporaryHasMessages.value = false;
});
</script>

<style scoped lang="less">
.agent-chat-container {
  display: flex;
  height: 100%;
  background: #f5f5f5;
  overflow: hidden;
  --brand-primary: #1890ff;
}

.left-slide {
  width: 280px;
  height: 100%;
  background: #fff;
  border-right: 1px solid #f0f0f0;
  transition: all 0.3s;
  position: relative;

  &.collapsed {
    width: 0;
    overflow: hidden;
  }

  .slide-toggle-btn {
    position: absolute;
    top: 50%;
    right: -12px;
    transform: translateY(-50%);
    width: 24px;
    height: 48px;
    background: #fff;
    border: 1px solid #f0f0f0;
    border-radius: 0 8px 8px 0;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    z-index: 10;
    transition: all 0.2s;

    &:hover {
      background: #f5f5f5;
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

  &.expanded {
    margin-left: 0;
  }
}

.chat-header {
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;

  .header-title {
    display: flex;
    align-items: center;
    gap: 10px;

    .title-icon {
      font-size: 24px;
      color: var(--brand-primary);
    }

    .brand-logo {
      width: 24px;
      height: 24px;
      border-radius: 4px;
      object-fit: contain;
    }

    h2 {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      color: #262626;
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .header-help-button {
    padding: 0 8px;
  }

  .header-version-tag {
    margin-left: 4px;
    background: #f0f5ff;
    color: #2f54eb;
    border-color: #adc6ff;
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px 20px;
  background: #fff;
  min-height: 0;

  // 滚动条样式
  &::-webkit-scrollbar {
    width: 8px;
  }

  &::-webkit-scrollbar-track {
    background: #f5f5f5;
  }

  &::-webkit-scrollbar-thumb {
    background: #d9d9d9;
    border-radius: 4px;

    &:hover {
      background: #bfbfbf;
    }
  }
}

.welcome-message {
  max-width: 600px;
  margin: 30px auto;
  text-align: center;

  .welcome-icon {
    font-size: 48px;
    margin-bottom: 16px;
  }

  h3 {
    font-size: 20px;
    font-weight: 600;
    color: #262626;
    margin-bottom: 12px;
  }

  p {
    font-size: 14px;
    color: #595959;
    margin-bottom: 16px;
  }

  .welcome-subtitle {
    color: #8c8c8c;
    margin-bottom: 18px;
  }

  .welcome-capabilities {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 18px;
  }

  .capability-card {
    text-align: left;
    background: #f7f9fc;
    border: 1px solid #edf1f7;
    border-radius: 10px;
    padding: 12px;
    transition: all 0.2s;

    &:hover {
      border-color: #cfe3ff;
      background: #ffffff;
      box-shadow: 0 4px 12px rgba(24, 144, 255, 0.08);
    }
  }

  .capability-icon {
    font-size: 18px;
    margin-bottom: 6px;
  }

  .capability-title {
    font-size: 14px;
    font-weight: 600;
    color: #262626;
    margin-bottom: 4px;
  }

  .capability-desc {
    font-size: 12px;
    color: #8c8c8c;
    line-height: 1.5;
  }

  .welcome-scenarios {
    margin-bottom: 16px;
    text-align: left;

    .section-title {
      font-size: 13px;
      font-weight: 600;
      color: #262626;
      margin-bottom: 8px;
    }

    .scenario-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  }

  .scenario-tag {
    cursor: pointer;
    border-radius: 16px;
    padding: 2px 10px;
    font-size: 12px;
    color: #1d39c4;
    background: #f0f5ff;
    border: 1px solid #adc6ff;

    &:hover {
      color: #10239e;
      border-color: #85a5ff;
    }
  }

  ul {
    text-align: left;
    list-style: none;
    padding: 0;
    margin-bottom: 20px;

    li {
      padding: 10px 12px;
      margin-bottom: 6px;
      background: #f5f5f5;
      border-radius: 8px;
      font-size: 13px;
      color: #262626;
      line-height: 1.5;
    }
  }

  .welcome-hint {
    color: #8c8c8c;
    font-style: italic;
    font-size: 13px;
  }

}

.chat-footer {
  background: #fff;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;

  .status-bar {
    padding: 6px 20px;
    background: #f0f2f5;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: #595959;
  }

  // 输入框容器（参考豆包设计）
  .input-container {
    padding: 12px 20px;
  }

  .input-wrapper {
    position: relative;
    background: #f5f5f5;
    border-radius: 12px;
    border: 1px solid #e8e8e8;
    transition: all 0.2s;
    padding: 12px;
    min-height: 120px;

    &:focus-within {
      border-color: #1890ff;
      background: #fff;
      box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
    }

    // 输入框样式
    :deep(.ant-input) {
      background: transparent;
      border: none;
      box-shadow: none;
      padding: 0;
      padding-bottom: 40px; // 为底部按钮留出空间
      resize: none;
      font-size: 14px;
      line-height: 1.6;
      min-height: 80px;

      &:focus,
      &:hover {
        border-color: transparent;
        box-shadow: none;
      }

      &::placeholder {
        color: #bfbfbf;
      }
    }

    // 左下角：模型选择器
    .input-bottom-left {
      position: absolute;
      bottom: 8px;
      left: 12px;
      z-index: 10;

      :deep(.agent-model-selector) {
        .ant-select {
          .ant-select-selector {
            background: transparent;
            border: none;
            box-shadow: none;
            padding: 0 20px 0 0;
            min-height: auto;
            height: auto;
          }

          .ant-select-selection-item {
            padding: 0;
            line-height: 1.5;
            font-size: 13px;
            color: #595959;
          }

          .ant-select-arrow {
            right: 0;
            font-size: 12px;
            color: #8c8c8c;
          }

          &:hover .ant-select-selector,
          &.ant-select-focused .ant-select-selector {
            border-color: transparent;
            background: transparent;
          }
        }
      }
    }

    // 右下角：发送按钮
    .input-bottom-right {
      position: absolute;
      bottom: 8px;
      right: 12px;
      z-index: 10;

      .action-button {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 32px;
        height: 32px;
        padding: 0;
        border: none;
        border-radius: 6px;
        transition: all 0.2s;

        &:hover:not(:disabled) {
          background: #e6f7ff;
          color: #1890ff;
        }

        &.send-button {
          &:not(:disabled) {
            background: #1890ff;
            color: #fff;

            &:hover {
              background: #40a9ff;
            }
          }

          &:disabled {
            color: #bfbfbf;
            background: transparent;
            cursor: not-allowed;
          }
        }

        .anticon {
          font-size: 16px;
        }
      }
    }
  }
}

.config-content {
  .config-item {
    margin-bottom: 24px;

    .config-label {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 8px;
      font-size: 13px;
      font-weight: 500;
      color: #262626;
    }

    .config-hint {
      margin-top: 8px;
      padding: 8px 12px;
      background: #f0f2f5;
      border-radius: 6px;
      font-size: 12px;
      color: #595959;
      line-height: 1.6;
    }
  }
}
</style>

