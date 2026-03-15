<template>
  <div class="agent-list-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <div class="page-title-group">
          <robot-outlined class="title-icon" />
          <div>
            <h1 class="page-title">{{ t('agentList.title') }}</h1>
            <p class="page-subtitle">{{ t('agentList.subtitle') }}</p>
          </div>
        </div>
      </div>
      <a-button type="primary" class="create-btn" @click="goToCreate">
        <template #icon><plus-outlined /></template>
        {{ t('agentList.create') }}
      </a-button>
    </div>

    <!-- 初次加载骨架屏 -->
    <div v-if="initialLoading" class="loading-state">
      <a-spin size="large" />
    </div>

    <!-- Agent 卡片网格 -->
    <div v-else class="agent-grid">
      <div
        v-for="agent in agents"
        :key="agent.id"
        class="agent-card"
      >
        <!-- 卡片头部 -->
        <div class="card-header">
          <div class="card-avatar">
            <robot-outlined />
          </div>
          <div class="card-title-group">
            <div class="card-name">
              <span v-if="agent.builtin" class="builtin-badge">{{ t('agentList.builtin') }}</span>
              {{ agent.name }}
            </div>
            <div class="card-meta">
              <clock-circle-outlined class="meta-icon" />
              {{ formatTime(agent.createTime) }}
            </div>
          </div>
        </div>

        <!-- 描述 -->
        <div class="card-desc">
          {{ agent.description || t('agentList.noDesc') }}
        </div>

        <!-- 系统提示词预览 -->
        <div v-if="agent.systemPrompt" class="card-prompt">
          <span class="prompt-label">Prompt</span>
          <span class="prompt-preview">{{ truncate(agent.systemPrompt, 100) }}</span>
        </div>

        <!-- 工具 Tags -->
        <div class="card-tools">
          <template v-if="hasTools(agent)">
            <a-tag
              v-for="group in (agent.tools?.mcpGroups || [])"
              :key="'mcp-' + group"
              class="tool-tag mcp-tag"
            >
              {{ group }}
            </a-tag>
            <a-tag
              v-for="tool in (agent.tools?.systemTools || [])"
              :key="'sys-' + tool"
              class="tool-tag sys-tag"
            >
              {{ tool }}
            </a-tag>
          </template>
          <span v-else class="no-tools">{{ t('agentList.noTools') }}</span>
        </div>

        <!-- 知识库 Tags -->
        <div v-if="(agent.tools?.knowledgeIds?.length || 0) > 0" class="card-knowledge">
          <database-outlined class="knowledge-icon" />
          <a-tag
            v-for="kbId in (agent.tools?.knowledgeIds || [])"
            :key="'kb-' + kbId"
            class="tool-tag kb-tag"
          >
            {{ getKbName(kbId) }}
          </a-tag>
        </div>

        <!-- 操作按钮 -->
        <div class="card-actions">
          <a-button type="primary" size="small" class="action-btn chat-btn" @click="goToChat(agent)">
            <template #icon><message-outlined /></template>
            {{ t('agentList.chat') }}
          </a-button>
          <a-button type="text" size="small" class="action-btn edit-btn" @click="goToEdit(agent)">
            <template #icon><edit-outlined /></template>
            {{ t('agentList.edit') }}
          </a-button>
          <a-popconfirm
            v-if="!agent.builtin"
            :title="t('agentList.deleteConfirmTitle', { name: agent.name })"
            :description="t('agentList.deleteDesc')"
            :ok-text="t('agentList.delete')"
            ok-type="danger"
            :cancel-text="t('common.cancel')"
            placement="topRight"
            @confirm="handleDelete(agent)"
          >
            <a-button type="text" size="small" danger class="action-btn delete-btn">
              <template #icon><delete-outlined /></template>
              {{ t('agentList.delete') }}
            </a-button>
          </a-popconfirm>
          <span v-else class="builtin-lock">
            <lock-outlined />
            {{ t('agentList.builtinLock') }}
          </span>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="agents.length === 0" class="empty-state">
        <robot-outlined class="empty-icon" />
        <p class="empty-text">{{ t('agentList.empty') }}</p>
        <p class="empty-hint">{{ t('agentList.emptyHint') }}</p>
        <a-button type="primary" @click="goToCreate">
          <template #icon><plus-outlined /></template>
          {{ t('agentList.create') }}
        </a-button>
      </div>
    </div>

    <!-- 加载更多触发器（IntersectionObserver 锚点） -->
    <div ref="loadMoreAnchor" class="load-more-anchor">
      <a-spin v-if="loadingMore" size="small" />
      <span v-else-if="noMore && agents.length > 0" class="no-more-text">{{ t('agentList.loadedAll') }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { message } from 'ant-design-vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import {
  RobotOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ClockCircleOutlined,
  LockOutlined,
  MessageOutlined,
  DatabaseOutlined,
} from '@ant-design/icons-vue';
import {
  getAgentDefinitionsPage,
  deleteAgentDefinition,
  getKnowledgeList,
} from '../agent/agent.api';
import type { AgentDefinition, KnowledgeInfo } from '../agent/agent.types';

// ─── 常量 ──────────────────────────────────────────────────────────────────

const PAGE_SIZE = 12;

// ─── 数据 ──────────────────────────────────────────────────────────────────

const router = useRouter();
const { t } = useI18n();

const initialLoading = ref(false);
const loadingMore = ref(false);
const noMore = ref(false);
const currentPage = ref(0);
const agents = ref<AgentDefinition[]>([]);
const knowledgeBases = ref<KnowledgeInfo[]>([]);

const loadMoreAnchor = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | null = null;

// ─── 加载 ───────────────────────────────────────────────────────────────────

async function loadNextPage() {
  if (loadingMore.value || noMore.value) return;

  const nextPage = currentPage.value + 1;
  loadingMore.value = true;
  try {
    const result = await getAgentDefinitionsPage(nextPage, PAGE_SIZE);
    if (!result) return;

    agents.value.push(...result.records);
    currentPage.value = nextPage;

    if (agents.value.length >= result.total) {
      noMore.value = true;
    }
  } catch {
    message.error(t('agentList.loadFailed'));
  } finally {
    loadingMore.value = false;
  }
}

async function loadInitial() {
  initialLoading.value = true;
  currentPage.value = 0;
  agents.value = [];
  noMore.value = false;
  loadingMore.value = false;

  try {
    const [pageResult, kbList] = await Promise.all([
      getAgentDefinitionsPage(1, PAGE_SIZE),
      getKnowledgeList(),
    ]);

    knowledgeBases.value = kbList;

    if (pageResult) {
      agents.value = pageResult.records;
      currentPage.value = 1;
      if (agents.value.length >= pageResult.total) {
        noMore.value = true;
      }
    }
  } catch {
    message.error(t('agentList.loadFailed'));
  } finally {
    initialLoading.value = false;
  }
}

// ─── IntersectionObserver ───────────────────────────────────────────────────

function setupObserver() {
  if (!loadMoreAnchor.value) return;
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) {
        loadNextPage();
      }
    },
    { rootMargin: '120px' }
  );
  observer.observe(loadMoreAnchor.value);
}

onMounted(async () => {
  await loadInitial();
  setupObserver();
});

onBeforeUnmount(() => {
  observer?.disconnect();
});

// ─── 路由跳转 ────────────────────────────────────────────────────────────────

function goToCreate() {
  router.push({ name: 'AgentNew' });
}

function goToEdit(agent: AgentDefinition) {
  router.push({ name: 'AgentEdit', params: { id: agent.id } });
}

function goToChat(agent: AgentDefinition) {
  router.push({ path: '/agent', query: { agentId: agent.id } });
}

// ─── 删除 ───────────────────────────────────────────────────────────────────

async function handleDelete(agent: AgentDefinition) {
  const ok = await deleteAgentDefinition(agent.id);
  if (ok) {
    message.success(t('agentList.deleteSuccess'));
    await loadInitial();
  } else {
    message.error(t('agentList.deleteFailed'));
  }
}

// ─── 工具函数 ───────────────────────────────────────────────────────────────

function truncate(text: string, maxLen: number) {
  return text.length > maxLen ? text.slice(0, maxLen) + '...' : text;
}

function hasTools(agent: AgentDefinition) {
  return (agent.tools?.mcpGroups?.length || 0) + (agent.tools?.systemTools?.length || 0) > 0;
}

function getKbName(kbId: string) {
  const kb = knowledgeBases.value.find((k) => k.id === kbId);
  return kb ? kb.name : kbId;
}

function formatTime(time?: string) {
  if (!time) return '-';
  return new Date(time).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}
</script>

<style scoped lang="less">
.agent-list-page {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px;
  background: transparent;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 3px;
  }
}

// ─── 页面头部 ────────────────────────────────────────────────────────────────

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}

.header-left {
  display: flex;
  align-items: center;
}

.page-title-group {
  display: flex;
  align-items: center;
  gap: 14px;
}

.title-icon {
  font-size: 32px;
  color: #60a5fa;
  filter: drop-shadow(0 0 8px rgba(96, 165, 250, 0.5));
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #f1f5f9;
  letter-spacing: -0.5px;
  font-family: 'Inter', sans-serif;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
}

.create-btn {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  height: 36px;
  padding: 0 18px;
  font-weight: 500;
  border-radius: 6px;
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.3);

  &:hover {
    background: linear-gradient(135deg, #60a5fa, #3b82f6);
    box-shadow: 0 0 18px rgba(59, 130, 246, 0.5);
  }
}

// ─── 加载态 ──────────────────────────────────────────────────────────────────

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
}

// ─── 卡片网格 ────────────────────────────────────────────────────────────────

.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.agent-card {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 10px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  backdrop-filter: blur(8px);
  transition: border-color 0.2s, box-shadow 0.2s;

  &:hover {
    border-color: rgba(59, 130, 246, 0.35);
    box-shadow: 0 4px 20px rgba(59, 130, 246, 0.1);
  }
}

// ─── 卡片头部 ────────────────────────────────────────────────────────────────

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-avatar {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: rgba(59, 130, 246, 0.12);
  border: 1px solid rgba(59, 130, 246, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #60a5fa;
  flex-shrink: 0;
}

.card-title-group {
  flex: 1;
  min-width: 0;
}

.card-name {
  font-size: 15px;
  font-weight: 600;
  color: #e2e8f0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: 'Inter', sans-serif;
}

.card-meta {
  font-size: 11px;
  color: #475569;
  margin-top: 3px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-family: 'JetBrains Mono', monospace;
}

.meta-icon {
  font-size: 10px;
}

.builtin-badge {
  font-size: 10px;
  padding: 1px 6px;
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
  border-radius: 3px;
  border: 1px solid rgba(59, 130, 246, 0.25);
  font-weight: 500;
  flex-shrink: 0;
}

// ─── 描述 ────────────────────────────────────────────────────────────────────

.card-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
  min-height: 20px;
}

// ─── Prompt 预览 ─────────────────────────────────────────────────────────────

.card-prompt {
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.prompt-label {
  color: #60a5fa;
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
  font-size: 11px;
  flex-shrink: 0;
  margin-top: 1px;
}

.prompt-preview {
  color: #94a3b8;
  line-height: 1.5;
  font-family: 'JetBrains Mono', monospace;
  word-break: break-all;
}

// ─── 知识库 Tags ──────────────────────────────────────────────────────────────

.card-knowledge {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.knowledge-icon {
  font-size: 12px;
  color: #f59e0b;
  flex-shrink: 0;
}

.kb-tag {
  background: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.25);
  color: #fbbf24;
}

// ─── 工具 Tags ───────────────────────────────────────────────────────────────

.card-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 24px;
  align-items: center;
}

.tool-tag {
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
  border-radius: 3px;
  padding: 0 6px;
  line-height: 20px;
  height: 20px;
}

.mcp-tag {
  background: rgba(139, 92, 246, 0.12);
  border-color: rgba(139, 92, 246, 0.3);
  color: #a78bfa;
}

.sys-tag {
  background: rgba(16, 185, 129, 0.1);
  border-color: rgba(16, 185, 129, 0.25);
  color: #34d399;
}

.no-tools {
  font-size: 11px;
  color: #334155;
  font-family: 'JetBrains Mono', monospace;
}

// ─── 操作按钮 ────────────────────────────────────────────────────────────────

.card-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-top: 8px;
  border-top: 1px solid rgba(59, 130, 246, 0.08);
  margin-top: auto;
}

.action-btn {
  font-size: 12px;
  height: 28px;
  padding: 0 10px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', monospace;
}

.chat-btn {
  background: rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.3);
  color: #60a5fa;
  font-size: 12px;

  &:hover {
    background: rgba(59, 130, 246, 0.25);
    border-color: #60a5fa;
    color: #93c5fd;
  }
}

.edit-btn {
  color: #94a3b8;

  &:hover {
    color: #60a5fa;
    background: rgba(59, 130, 246, 0.08);
  }
}

.delete-btn {
  &:hover {
    background: rgba(239, 68, 68, 0.08);
  }
}

.builtin-lock {
  margin-left: auto;
  font-size: 11px;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 4px;
  font-family: 'JetBrains Mono', monospace;
}

// ─── 空状态 ──────────────────────────────────────────────────────────────────

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  gap: 12px;
}

.empty-icon {
  font-size: 56px;
  color: rgba(59, 130, 246, 0.2);
}

.empty-text {
  font-size: 16px;
  color: #475569;
  margin: 0;
}

.empty-hint {
  font-size: 13px;
  color: #334155;
  margin: 0 0 8px;
}

// ─── 加载更多触发器 ───────────────────────────────────────────────────────────

.load-more-anchor {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px 0 8px;
  min-height: 44px;
}

.no-more-text {
  font-size: 12px;
  color: #334155;
  font-family: 'JetBrains Mono', monospace;
}
</style>
