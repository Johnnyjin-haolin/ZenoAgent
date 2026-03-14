<template>
  <div class="agent-list-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <div class="page-title-group">
          <robot-outlined class="title-icon" />
          <div>
            <h1 class="page-title">Agent 管理</h1>
            <p class="page-subtitle">创建和管理自定义 Agent，配置系统提示词与工具能力</p>
          </div>
        </div>
      </div>
      <a-button type="primary" class="create-btn" @click="openCreateModal">
        <template #icon><plus-outlined /></template>
        新建 Agent
      </a-button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
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
              <span v-if="agent.builtin" class="builtin-badge">系统内置</span>
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
          {{ agent.description || '暂无描述' }}
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
          <span v-else class="no-tools">未配置工具</span>
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
            开始对话
          </a-button>
          <a-button type="text" size="small" class="action-btn edit-btn" @click="openEditModal(agent)">
            <template #icon><edit-outlined /></template>
            编辑
          </a-button>
          <a-popconfirm
            v-if="!agent.builtin"
            :title="`确认删除 Agent「${agent.name}」？`"
            description="删除后不可恢复"
            ok-text="删除"
            ok-type="danger"
            cancel-text="取消"
            placement="topRight"
            @confirm="handleDelete(agent)"
          >
            <a-button type="text" size="small" danger class="action-btn delete-btn">
              <template #icon><delete-outlined /></template>
              删除
            </a-button>
          </a-popconfirm>
          <span v-else class="builtin-lock">
            <lock-outlined />
            内置不可删除
          </span>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="agents.length === 0" class="empty-state">
        <robot-outlined class="empty-icon" />
        <p class="empty-text">暂无 Agent</p>
        <p class="empty-hint">点击「新建 Agent」创建第一个自定义 Agent</p>
        <a-button type="primary" @click="openCreateModal">
          <template #icon><plus-outlined /></template>
          新建 Agent
        </a-button>
      </div>
    </div>

    <!-- 新建 / 编辑 Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="editingId ? '编辑 Agent' : '新建 Agent'"
      width="640px"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      class="agent-form-modal"
      @ok="handleSave"
      @cancel="closeModal"
    >
      <div class="modal-form">
        <div class="form-item">
          <label class="form-label">名称 <span class="required">*</span></label>
          <a-input
            v-model:value="form.name"
            placeholder="输入 Agent 名称"
            :disabled="editingBuiltin"
            class="tech-input"
            :maxlength="50"
          />
        </div>

        <div class="form-item">
          <label class="form-label">描述</label>
          <a-textarea
            v-model:value="form.description"
            placeholder="描述该 Agent 的用途（可选）"
            :rows="2"
            :disabled="editingBuiltin"
            class="tech-input"
            :maxlength="200"
          />
        </div>

        <div class="form-item">
          <label class="form-label">系统提示词</label>
          <a-textarea
            v-model:value="form.systemPrompt"
            placeholder="输入系统提示词，定义 Agent 的行为和角色..."
            :rows="6"
            class="tech-input"
          />
        </div>

        <div class="form-item">
          <label class="form-label">MCP 工具分组</label>
          <a-select
            v-model:value="form.mcpGroups"
            mode="multiple"
            placeholder="选择启用的 MCP 工具分组（留空表示允许全部）"
            :options="mcpGroupOptions"
            allow-clear
            class="tech-select-multi"
          />
        </div>

        <div class="form-item">
          <label class="form-label">系统内置工具</label>
          <a-select
            v-model:value="form.systemTools"
            mode="multiple"
            placeholder="选择启用的系统工具（留空表示允许全部）"
            :options="systemToolOptions"
            allow-clear
            class="tech-select-multi"
          />
        </div>

        <div class="form-item">
          <label class="form-label">关联知识库</label>
          <a-select
            v-model:value="form.knowledgeIds"
            mode="multiple"
            placeholder="选择关联的知识库（对话时自动检索）"
            :options="knowledgeOptions"
            allow-clear
            class="tech-select-multi"
          />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { useRouter } from 'vue-router';
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
  getAgentDefinitions,
  createAgentDefinition,
  updateAgentDefinition,
  deleteAgentDefinition,
  getAvailableMcpGroupsForAgent,
  getAvailableSystemToolsForAgent,
  getKnowledgeList,
} from '../agent/agent.api';
import type { AgentDefinition, KnowledgeInfo } from '../agent/agent.types';

// ─── 数据 ──────────────────────────────────────────────────────────────────

const router = useRouter();

const loading = ref(false);
const saving = ref(false);
const agents = ref<AgentDefinition[]>([]);
const knowledgeBases = ref<KnowledgeInfo[]>([]);
const mcpGroupOptions = ref<{ label: string; value: string }[]>([]);
const systemToolOptions = ref<{ label: string; value: string }[]>([]);
const knowledgeOptions = ref<{ label: string; value: string }[]>([]);

// ─── Modal 状态 ─────────────────────────────────────────────────────────────

const modalVisible = ref(false);
const editingId = ref<string | null>(null);
const editingBuiltin = ref(false);

const form = ref({
  name: '',
  description: '',
  systemPrompt: '',
  mcpGroups: [] as string[],
  systemTools: [] as string[],
  knowledgeIds: [] as string[],
});

// ─── 加载 ───────────────────────────────────────────────────────────────────

async function loadData() {
  loading.value = true;
  try {
    const [agentList, groups, tools, kbList] = await Promise.all([
      getAgentDefinitions(),
      getAvailableMcpGroupsForAgent(),
      getAvailableSystemToolsForAgent(),
      getKnowledgeList(),
    ]);
    agents.value = agentList;
    knowledgeBases.value = kbList;
    mcpGroupOptions.value = groups.map((g) => ({
      label: g.name || g.id,
      value: g.id,
    }));
    systemToolOptions.value = tools.map((t) => ({
      label: `${t.name}${t.description ? ' — ' + t.description : ''}`,
      value: t.name,
    }));
    knowledgeOptions.value = kbList.map((kb) => ({
      label: kb.name,
      value: kb.id,
    }));
  } catch {
    message.error('加载 Agent 列表失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadData();
});

// ─── Modal 操作 ─────────────────────────────────────────────────────────────

function openCreateModal() {
  editingId.value = null;
  editingBuiltin.value = false;
  form.value = { name: '', description: '', systemPrompt: '', mcpGroups: [], systemTools: [], knowledgeIds: [] };
  modalVisible.value = true;
}

function openEditModal(agent: AgentDefinition) {
  editingId.value = agent.id;
  editingBuiltin.value = agent.builtin;
  form.value = {
    name: agent.name,
    description: agent.description || '',
    systemPrompt: agent.systemPrompt || '',
    mcpGroups: agent.tools?.mcpGroups || [],
    systemTools: agent.tools?.systemTools || [],
    knowledgeIds: agent.tools?.knowledgeIds || [],
  };
  modalVisible.value = true;
}

function closeModal() {
  modalVisible.value = false;
}

async function handleSave() {
  if (!form.value.name.trim()) {
    message.warning('请输入 Agent 名称');
    return;
  }
  saving.value = true;
  try {
    const request = {
      name: form.value.name.trim(),
      description: form.value.description.trim(),
      systemPrompt: form.value.systemPrompt,
      tools: {
        mcpGroups: form.value.mcpGroups,
        systemTools: form.value.systemTools,
        knowledgeIds: form.value.knowledgeIds,
      },
    };

    if (editingId.value) {
      const result = await updateAgentDefinition(editingId.value, request);
      if (result) {
        message.success('保存成功');
        modalVisible.value = false;
        await loadData();
      } else {
        message.error('保存失败');
      }
    } else {
      const result = await createAgentDefinition(request);
      if (result) {
        message.success('创建成功');
        modalVisible.value = false;
        await loadData();
      } else {
        message.error('创建失败');
      }
    }
  } finally {
    saving.value = false;
  }
}

async function handleDelete(agent: AgentDefinition) {
  const ok = await deleteAgentDefinition(agent.id);
  if (ok) {
    message.success('删除成功');
    await loadData();
  } else {
    message.error('删除失败');
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

function goToChat(agent: AgentDefinition) {
  router.push({ path: '/agent', query: { agentId: agent.id } });
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
</style>

<style lang="less">
.agent-form-modal {
  .ant-modal-content {
    background: #0f172a;
    border: 1px solid rgba(59, 130, 246, 0.2);
    border-radius: 10px;
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.6);
  }

  .ant-modal-header {
    background: transparent;
    border-bottom: 1px solid rgba(59, 130, 246, 0.15);
    padding: 16px 20px;

    .ant-modal-title {
      color: #e2e8f0;
      font-size: 15px;
      font-weight: 600;
      font-family: 'Inter', sans-serif;
    }
  }

  .ant-modal-body {
    padding: 20px;
  }

  .ant-modal-footer {
    border-top: 1px solid rgba(59, 130, 246, 0.15);
    padding: 12px 20px;

    .ant-btn-default {
      background: rgba(255, 255, 255, 0.05);
      border-color: rgba(255, 255, 255, 0.1);
      color: #94a3b8;

      &:hover {
        border-color: rgba(59, 130, 246, 0.4);
        color: #e2e8f0;
      }
    }
  }

  .ant-modal-close {
    color: #64748b;

    &:hover {
      color: #e2e8f0;
    }
  }
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 12px;
  color: #94a3b8;
  font-family: 'JetBrains Mono', monospace;
  font-weight: 500;
}

.required {
  color: #f87171;
}

.tech-input {
  background: rgba(0, 0, 0, 0.2) !important;
  border-color: rgba(59, 130, 246, 0.2) !important;
  color: #e2e8f0 !important;
  font-size: 13px;
  font-family: 'JetBrains Mono', monospace;
  border-radius: 6px;

  &:hover,
  &:focus {
    border-color: #60a5fa !important;
    box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1) !important;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.tech-select-multi {
  width: 100%;

  .ant-select-selector {
    background: rgba(0, 0, 0, 0.2) !important;
    border-color: rgba(59, 130, 246, 0.2) !important;
    color: #e2e8f0 !important;
    min-height: 36px;
    border-radius: 6px !important;

    &:hover {
      border-color: #60a5fa !important;
    }
  }

  .ant-select-selection-item {
    background: rgba(59, 130, 246, 0.15);
    border-color: rgba(59, 130, 246, 0.3);
    color: #60a5fa;
    font-size: 12px;
    border-radius: 3px;
  }

  .ant-select-selection-placeholder {
    color: rgba(148, 163, 184, 0.4);
    font-size: 12px;
  }
}
</style>
