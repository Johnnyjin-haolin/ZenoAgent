<template>
  <a-modal
    v-model:open="visible"
    title="Agent 管理"
    width="780px"
    :footer="null"
    :mask-closable="true"
    class="agent-config-modal"
    @cancel="handleClose"
  >
    <div class="modal-body">
      <!-- 左侧：Agent 列表 -->
      <div class="agent-list-panel">
        <div class="panel-header">
          <span class="panel-title">Agent 列表</span>
          <a-button type="primary" size="small" @click="handleCreateNew">
            <template #icon><Icon icon="ant-design:plus-outlined" /></template>
            新建
          </a-button>
        </div>

        <div class="agent-list">
          <div
            v-for="agent in agents"
            :key="agent.id"
            class="agent-item"
            :class="{ active: editingAgent?.id === agent.id }"
            @click="handleSelect(agent)"
          >
            <div class="agent-item-info">
              <div class="agent-item-name">
                <span v-if="agent.builtin" class="builtin-badge">系统</span>
                {{ agent.name }}
              </div>
              <div v-if="agent.description" class="agent-item-desc">
                {{ agent.description }}
              </div>
            </div>
            <a-button
              v-if="!agent.builtin"
              type="text"
              size="small"
              danger
              class="delete-btn"
              @click.stop="handleDelete(agent)"
            >
              <template #icon><Icon icon="ant-design:delete-outlined" /></template>
            </a-button>
          </div>

          <div v-if="agents.length === 0" class="empty-list">
            暂无 Agent
          </div>
        </div>
      </div>

      <!-- 右侧：编辑表单 -->
      <div class="agent-edit-panel">
        <template v-if="editingAgent">
          <div class="panel-header">
            <span class="panel-title">{{ editingAgent.id ? '编辑 Agent' : '新建 Agent' }}</span>
          </div>

          <div class="edit-form">
            <div class="form-item">
              <label class="form-label">名称 <span class="required">*</span></label>
              <a-input
                v-model:value="editingAgent.name"
                placeholder="输入 Agent 名称"
                :disabled="editingAgent.builtin"
                class="tech-input"
              />
            </div>

            <div class="form-item">
              <label class="form-label">描述</label>
              <a-textarea
                v-model:value="editingAgent.description"
                placeholder="描述 Agent 的用途（可选）"
                :rows="2"
                :disabled="editingAgent.builtin"
                class="tech-input"
              />
            </div>

            <div class="form-item">
              <label class="form-label">系统提示词</label>
              <a-textarea
                v-model:value="editingAgent.systemPrompt"
                placeholder="输入系统提示词，定义 Agent 的行为和角色"
                :rows="5"
                class="tech-input"
              />
            </div>

            <div class="form-item">
              <label class="form-label">MCP 工具分组</label>
              <a-select
                v-model:value="editingMcpGroups"
                mode="multiple"
                placeholder="选择 MCP 工具分组"
                :options="mcpGroupOptions"
                allow-clear
                class="tech-select-multi"
              />
            </div>

            <div class="form-item">
              <label class="form-label">系统内置工具</label>
              <a-select
                v-model:value="editingSystemTools"
                mode="multiple"
                placeholder="选择系统工具"
                :options="systemToolOptions"
                allow-clear
                class="tech-select-multi"
              />
            </div>

            <div class="form-item">
              <label class="form-label">关联知识库</label>
              <a-select
                v-model:value="editingKnowledgeIds"
                mode="multiple"
                placeholder="选择关联的知识库（对话时自动检索）"
                :options="knowledgeOptions"
                allow-clear
                class="tech-select-multi"
              />
            </div>
          </div>

          <div class="form-actions">
            <a-button @click="handleCancelEdit">取消</a-button>
            <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
          </div>
        </template>

        <div v-else class="empty-edit">
          <Icon icon="ant-design:robot-outlined" class="empty-icon" />
          <p>选择左侧 Agent 进行编辑</p>
          <p class="empty-hint">或点击「新建」创建自定义 Agent</p>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { Icon } from '@/components/Icon';
import {
  getAgentDefinitions,
  createAgentDefinition,
  updateAgentDefinition,
  deleteAgentDefinition,
  getAvailableMcpGroupsForAgent,
  getAvailableSystemToolsForAgent,
  getKnowledgeList,
} from '../agent.api';
import type { AgentDefinition, AgentDefinitionRequest } from '../agent.types';

const props = defineProps<{
  open: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'change'): void;
}>();

const visible = computed({
  get: () => props.open,
  set: (val) => emit('update:open', val),
});

// ─── 数据 ───────────────────────────────────────────────────────────────────

const agents = ref<AgentDefinition[]>([]);
const editingAgent = ref<(AgentDefinitionRequest & { id?: string; builtin?: boolean }) | null>(null);
const editingMcpGroups = ref<string[]>([]);
const editingSystemTools = ref<string[]>([]);
const editingKnowledgeIds = ref<string[]>([]);
const saving = ref(false);

const mcpGroupOptions = ref<{ label: string; value: string }[]>([]);
const systemToolOptions = ref<{ label: string; value: string }[]>([]);
const knowledgeOptions = ref<{ label: string; value: string }[]>([]);

// ─── 加载 ───────────────────────────────────────────────────────────────────

async function loadAll() {
  const [agentList, groups, tools, kbList] = await Promise.all([
    getAgentDefinitions(),
    getAvailableMcpGroupsForAgent(),
    getAvailableSystemToolsForAgent(),
    getKnowledgeList(),
  ]);
  agents.value = agentList;
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
}

watch(
  () => props.open,
  (val) => {
    if (val) {
      loadAll();
      editingAgent.value = null;
    }
  }
);

onMounted(() => {
  if (props.open) loadAll();
});

// ─── 交互 ───────────────────────────────────────────────────────────────────

function handleSelect(agent: AgentDefinition) {
  editingAgent.value = {
    id: agent.id,
    name: agent.name,
    description: agent.description || '',
    systemPrompt: agent.systemPrompt || '',
    builtin: agent.builtin,
    tools: agent.tools,
  };
  editingMcpGroups.value = agent.tools?.mcpGroups || [];
  editingSystemTools.value = agent.tools?.systemTools || [];
  editingKnowledgeIds.value = agent.tools?.knowledgeIds || [];
}

function handleCreateNew() {
  editingAgent.value = {
    name: '',
    description: '',
    systemPrompt: '',
    builtin: false,
    tools: { mcpGroups: [], systemTools: [], knowledgeIds: [] },
  };
  editingMcpGroups.value = [];
  editingSystemTools.value = [];
  editingKnowledgeIds.value = [];
}

function handleCancelEdit() {
  editingAgent.value = null;
}

async function handleSave() {
  if (!editingAgent.value) return;
  if (!editingAgent.value.name?.trim()) {
    message.warning('请输入 Agent 名称');
    return;
  }

  saving.value = true;
  try {
    const request: AgentDefinitionRequest = {
      name: editingAgent.value.name,
      description: editingAgent.value.description,
      systemPrompt: editingAgent.value.systemPrompt,
      tools: {
        mcpGroups: editingMcpGroups.value,
        systemTools: editingSystemTools.value,
        knowledgeIds: editingKnowledgeIds.value,
      },
    };

    const agentId = (editingAgent.value as any).id;
    if (agentId) {
      const updated = await updateAgentDefinition(agentId, request);
      if (updated) {
        message.success('保存成功');
        emit('change');
        await loadAll();
        handleSelect(updated);
      } else {
        message.error('保存失败');
      }
    } else {
      const created = await createAgentDefinition(request);
      if (created) {
        message.success('创建成功');
        emit('change');
        await loadAll();
        handleSelect(created);
      } else {
        message.error('创建失败');
      }
    }
  } finally {
    saving.value = false;
  }
}

function handleDelete(agent: AgentDefinition) {
  Modal.confirm({
    title: `删除 Agent「${agent.name}」?`,
    content: '删除后不可恢复',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      const ok = await deleteAgentDefinition(agent.id);
      if (ok) {
        message.success('删除成功');
        emit('change');
        if (editingAgent.value && (editingAgent.value as any).id === agent.id) {
          editingAgent.value = null;
        }
        await loadAll();
      } else {
        message.error('删除失败');
      }
    },
  });
}

function handleClose() {
  emit('update:open', false);
}
</script>

<style scoped lang="less">
.modal-body {
  display: flex;
  gap: 16px;
  height: 520px;
}

// ─── 左侧列表 ───────────────────────────────────────────────────────────────

.agent-list-panel {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 6px;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(59, 130, 246, 0.15);
  flex-shrink: 0;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
  font-family: 'JetBrains Mono', monospace;
}

.agent-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(59, 130, 246, 0.3);
    border-radius: 2px;
  }
}

.agent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  margin-bottom: 4px;

  &:hover {
    background: rgba(59, 130, 246, 0.1);
  }

  &.active {
    background: rgba(59, 130, 246, 0.15);
    border: 1px solid rgba(59, 130, 246, 0.3);
  }
}

.agent-item-info {
  flex: 1;
  min-width: 0;
}

.agent-item-name {
  font-size: 12px;
  font-weight: 500;
  color: #e2e8f0;
  display: flex;
  align-items: center;
  gap: 5px;
  font-family: 'JetBrains Mono', monospace;
}

.agent-item-desc {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.builtin-badge {
  font-size: 10px;
  padding: 1px 4px;
  background: rgba(59, 130, 246, 0.2);
  color: #60a5fa;
  border-radius: 2px;
  line-height: 14px;
  flex-shrink: 0;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
  flex-shrink: 0;
  padding: 0;

  .agent-item:hover & {
    opacity: 1;
  }
}

.empty-list {
  padding: 20px;
  text-align: center;
  font-size: 12px;
  color: #475569;
}

// ─── 右侧编辑 ───────────────────────────────────────────────────────────────

.agent-edit-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 6px;
  overflow: hidden;
}

.edit-form {
  flex: 1;
  overflow-y: auto;
  padding: 16px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(59, 130, 246, 0.3);
    border-radius: 2px;
  }
}

.form-item {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
  font-family: 'JetBrains Mono', monospace;
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

  &:hover,
  &:focus {
    border-color: #60a5fa !important;
    box-shadow: none !important;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.tech-select-multi {
  width: 100%;

  :deep(.ant-select-selector) {
    background: rgba(0, 0, 0, 0.2) !important;
    border-color: rgba(59, 130, 246, 0.2) !important;
    color: #e2e8f0 !important;
    min-height: 32px;

    &:hover {
      border-color: #60a5fa !important;
    }
  }

  :deep(.ant-select-selection-item) {
    background: rgba(59, 130, 246, 0.15);
    border-color: rgba(59, 130, 246, 0.3);
    color: #60a5fa;
    font-size: 12px;
  }

  :deep(.ant-select-selection-placeholder) {
    color: rgba(148, 163, 184, 0.4);
    font-size: 12px;
  }
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid rgba(59, 130, 246, 0.15);
  flex-shrink: 0;
}

.empty-edit {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #475569;
  gap: 8px;
}

.empty-icon {
  font-size: 40px;
  color: rgba(59, 130, 246, 0.3);
}

.empty-hint {
  font-size: 12px;
  color: #334155;
}
</style>

<style lang="less">
.agent-config-modal {
  .ant-modal-content {
    background: #0f172a;
    border: 1px solid rgba(59, 130, 246, 0.2);
    border-radius: 8px;
  }

  .ant-modal-header {
    background: transparent;
    border-bottom: 1px solid rgba(59, 130, 246, 0.15);

    .ant-modal-title {
      color: #e2e8f0;
      font-family: 'JetBrains Mono', monospace;
    }
  }

  .ant-modal-close {
    color: #64748b;

    &:hover {
      color: #e2e8f0;
    }
  }

  .ant-modal-body {
    padding: 16px;
  }
}
</style>
