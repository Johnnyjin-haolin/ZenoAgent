<template>
  <div class="skill-list-page">
    <div class="page-header">
      <div class="header-left">
        <div class="page-title-group">
          <thunderbolt-outlined class="title-icon" />
          <div>
            <h1 class="page-title">{{ t('skill.title') }}</h1>
            <p class="page-subtitle">{{ t('skill.subtitle') }}</p>
          </div>
        </div>
      </div>
      <a-button type="primary" class="create-btn" @click="handleCreate">
        <template #icon><plus-outlined /></template>
        {{ t('skill.create') }}
      </a-button>
    </div>

    <div v-if="loading" class="loading-state">
      <a-spin size="large" />
    </div>

    <div v-else class="skill-grid">
      <div
        v-for="skill in skills"
        :key="skill.id"
        class="skill-card"
      >
        <div class="card-header">
          <div class="card-avatar">
            <thunderbolt-outlined />
          </div>
          <div class="card-title-group">
            <div class="card-name">{{ skill.name }}</div>
            <div class="card-id">
              <code-outlined class="meta-icon" />
              {{ skill.id.slice(0, 8) }}…
            </div>
          </div>
          <div class="card-actions">
            <a-button type="text" size="small" class="action-btn edit-btn" @click="handleEdit(skill)">
              <template #icon><edit-outlined /></template>
            </a-button>
            <a-popconfirm
              :title="t('skill.deleteConfirm')"
              :description="t('skill.deleteDesc')"
              :ok-text="t('common.delete')"
              ok-type="danger"
              :cancel-text="t('common.cancel')"
              placement="bottomRight"
              @confirm="handleDelete(skill.id)"
            >
              <a-button type="text" size="small" danger class="action-btn delete-btn">
                <template #icon><delete-outlined /></template>
              </a-button>
            </a-popconfirm>
          </div>
        </div>

        <div class="card-summary">{{ skill.summary || t('skill.noSummary') }}</div>

        <div class="card-tags">
          <a-tag
            v-for="tag in skill.tags"
            :key="tag"
            class="skill-tag"
          >{{ tag }}</a-tag>
          <span v-if="!skill.tags || skill.tags.length === 0" class="no-tags">{{ t('skill.noTags') }}</span>
        </div>
      </div>

      <div v-if="skills.length === 0" class="empty-state">
        <thunderbolt-outlined class="empty-icon" />
        <p class="empty-text">{{ t('skill.empty') }}</p>
        <p class="empty-hint">{{ t('skill.emptyHint') }}</p>
        <a-button type="primary" @click="handleCreate">
          <template #icon><plus-outlined /></template>
          {{ t('skill.create') }}
        </a-button>
      </div>
    </div>

    <SkillEditModal
      v-if="modalVisible"
      :skill="editingSkill"
      @close="modalVisible = false"
      @saved="handleSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { useI18n } from 'vue-i18n';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ThunderboltOutlined,
  CodeOutlined,
} from '@ant-design/icons-vue';
import { getSkillList, deleteSkill } from '@/api/agent-skill.api';
import type { AgentSkill } from '@/api/agent-skill.api';
import SkillEditModal from './components/SkillEditModal.vue';

const { t } = useI18n();

const loading = ref(false);
const skills = ref<AgentSkill[]>([]);
const modalVisible = ref(false);
const editingSkill = ref<AgentSkill | null>(null);

async function loadSkills() {
  loading.value = true;
  try {
    skills.value = await getSkillList();
  } finally {
    loading.value = false;
  }
}

function handleCreate() {
  editingSkill.value = null;
  modalVisible.value = true;
}

function handleEdit(skill: AgentSkill) {
  editingSkill.value = skill;
  modalVisible.value = true;
}

async function handleDelete(id: string) {
  try {
    await deleteSkill(id);
    message.success(t('skill.deleteSuccess'));
    await loadSkills();
  } catch {
    message.error(t('skill.deleteFailed'));
  }
}

function handleSaved() {
  modalVisible.value = false;
  loadSkills();
}

onMounted(loadSkills);
</script>

<style scoped lang="less">
.skill-list-page {
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

.skill-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.skill-card {
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
  font-family: 'Inter', sans-serif;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-id {
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

.card-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.action-btn {
  color: #475569;
  padding: 0 6px;
  height: 28px;
  border-radius: 4px;

  &:hover {
    background: rgba(59, 130, 246, 0.1);
    color: #60a5fa;
  }
}

.action-btn.delete-btn {
  &:hover {
    background: rgba(239, 68, 68, 0.1);
  }
}

// ─── 摘要 ────────────────────────────────────────────────────────────────────

.card-summary {
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

// ─── 标签 ────────────────────────────────────────────────────────────────────

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 22px;
}

.skill-tag {
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.25);
  color: #60a5fa;
  font-size: 11px;
  border-radius: 4px;
  padding: 0 7px;
  line-height: 20px;
}

.no-tags {
  font-size: 11px;
  color: #334155;
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
  font-size: 48px;
  color: #1e3a5f;
}

.empty-text {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #475569;
}

.empty-hint {
  margin: 0;
  font-size: 13px;
  color: #334155;
}
</style>
