<template>
  <div class="agent-chat-container">
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
          <h2>AI Agent 智能助手</h2>
        </div>
        
        <div class="header-actions">
          <a-button size="small" @click="showConfigDrawer = true">
            <template #icon>
              <Icon icon="ant-design:setting-outlined" />
            </template>
            配置
          </a-button>
        </div>
      </div>

      <!-- 消息列表区 -->
      <div ref="chatScrollRef" class="chat-messages">
        <template v-if="messages.length > 0">
          <AgentMessage
            v-for="msg in messages"
            :key="msg.id"
            :message="msg"
          />
        </template>
        
        <!-- 欢迎消息 -->
        <div v-else class="welcome-message">
          <div class="welcome-icon">🤖</div>
          <h3>欢迎使用 AI Agent 智能助手</h3>
          <p>我可以帮你：</p>
          <ul>
            <li>💡 智能问答 - 根据知识库提供准确回答</li>
            <li>🔧 工具调用 - 执行设备查询、命令等操作</li>
            <li>📊 数据分析 - 分析设备数据并提供见解</li>
            <li>🎯 任务编排 - 自动规划和执行复杂任务</li>
          </ul>
          <p class="welcome-hint">请在下方输入您的问题开始对话</p>
        </div>
      </div>

      <!-- 底部输入区 -->
      <div class="chat-footer">
        <!-- 当前状态提示 -->
        <div v-if="currentStatus" class="status-bar">
          <a-spin size="small" />
          <span>{{ currentStatus }}</span>
        </div>

        <!-- 输入框 -->
        <div class="input-area">
          <a-textarea
            ref="inputRef"
            v-model:value="userInput"
            :placeholder="inputPlaceholder"
            :auto-size="{ minRows: 1, maxRows: 6 }"
            :disabled="loading"
            @pressEnter="handlePressEnter"
          />
          
          <div class="input-actions">
            <a-button
              v-if="loading"
              type="primary"
              danger
              @click="handleStop"
            >
              <template #icon>
                <Icon icon="ant-design:stop-outlined" />
              </template>
              停止
            </a-button>
            <a-button
              v-else
              type="primary"
              :disabled="!userInput.trim()"
              @click="handleSend"
            >
              <template #icon>
                <Icon icon="ant-design:send-outlined" />
              </template>
              发送
            </a-button>
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
import { ref, computed, nextTick, onMounted } from 'vue';
import { Icon } from '@/components/Icon';
import { message } from 'ant-design-vue';
import { useAgentChat } from './hooks/useAgentChat';
import { getConversations, updateConversationTitle } from './agent.api';
import AgentMessage from './components/AgentMessage.vue';
import AgentSlide from './components/AgentSlide.vue';
import AgentModelSelector from './components/AgentModelSelector.vue';
import AgentKnowledgeSelector from './components/AgentKnowledgeSelector.vue';
import AgentToolConfig from './components/AgentToolConfig.vue';
import type { ConversationInfo, ModelInfo, KnowledgeInfo } from './agent.types';

// 会话管理
const conversations = ref<ConversationInfo[]>([]);
const currentConversationId = ref('');
const showSlide = ref(true);
const slideCollapsed = ref(false);

// 配置抽屉
const showConfigDrawer = ref(false);

// Agent 配置
const selectedModelId = ref('');
const selectedKnowledgeIds = ref<string[]>([]);
const selectedTools = ref<string[]>([]);
const executionMode = ref<'AUTO' | 'MANUAL'>('AUTO');

// 使用 Agent Chat Hook
const {
  messages,
  loading,
  currentStatus,
  sendMessage,
  stopGeneration,
  clearMessages,
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

// 加载会话列表
const loadConversations = async () => {
  try {
    const result = await getConversations();
    conversations.value = result;
    
    // 如果有会话，选择第一个
    if (result.length > 0 && !currentConversationId.value) {
      currentConversationId.value = result[0].id;
    }
  } catch (error) {
    console.error('加载会话列表失败:', error);
  }
};

// 选择会话
const handleSelectConversation = (conversation: ConversationInfo) => {
  currentConversationId.value = conversation.id;
  clearMessages();
  // TODO: 加载会话消息
};

// 新建会话
const handleNewConversation = () => {
  currentConversationId.value = '';
  clearMessages();
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

  userInput.value = '';
  
  await sendMessage(content, {
    modelId: selectedModelId.value,
    knowledgeIds: selectedKnowledgeIds.value,
    enabledTools: selectedTools.value,
    mode: executionMode.value,
  });
  
  // 滚动到底部
  await scrollToBottom();
};

// 停止生成
const handleStop = () => {
  stopGeneration();
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
};

const handleKnowledgeChange = (knowledgeIds: string[], knowledgeList: KnowledgeInfo[]) => {
  console.log('知识库变更:', knowledgeIds, knowledgeList);
};

const handleToolsChange = (tools: string[]) => {
  console.log('工具变更:', tools);
};

// 初始化
onMounted(() => {
  loadConversations();
});
</script>

<style scoped lang="less">
.agent-chat-container {
  display: flex;
  height: 100%;
  background: #f5f5f5;
  overflow: hidden;
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
      color: #1890ff;
    }

    h2 {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      color: #262626;
    }
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

  .input-area {
    padding: 12px 20px;
    display: flex;
    gap: 12px;
    align-items: flex-end;

    :deep(.ant-textarea) {
      flex: 1;
      resize: none;
    }

    .input-actions {
      flex-shrink: 0;
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

