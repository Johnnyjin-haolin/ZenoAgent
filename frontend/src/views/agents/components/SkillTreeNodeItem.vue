<template>
  <div
    class="tree-node"
    :class="{
      'is-dragging': draggingId === node.id,
    }"
  >
    <!-- 上方插入线 -->
    <div
      v-if="dragOverId === node.id && dropPosition === 'before'"
      class="drop-line drop-line-before"
    />

    <div
      class="node-row"
      :class="{
        'node-disabled': !node.enabled,
        'drag-over-inside': dragOverId === node.id && dropPosition === 'inside',
      }"
      draggable="true"
      @dragstart.stop="onDragStart"
      @dragover.prevent.stop="onDragOver"
      @dragleave.stop="onDragLeave"
      @drop.stop="onDrop"
      @dragend.stop="onDragEnd"
    >
      <!-- 折叠图标 -->
      <span v-if="isFolder" class="fold-icon" @click="folded = !folded">
        <right-outlined :class="{ 'fold-rotated': !folded }" />
      </span>
      <span v-else class="leaf-indent" />

      <!-- 节点图标 -->
      <folder-open-outlined v-if="isFolder && !folded" class="node-icon folder-icon" />
      <folder-outlined v-else-if="isFolder" class="node-icon folder-icon" />
      <thunderbolt-outlined v-else class="node-icon skill-icon" />

      <!-- 标签（双击重命名）-->
      <span v-if="!renaming" class="node-label" @dblclick="startRename">
        {{ node.label }}
      </span>
      <a-input
        v-else
        ref="renameInputRef"
        v-model:value="renameValue"
        size="small"
        class="rename-input"
        @blur="commitRename"
        @keyup.enter="commitRename"
        @keyup.esc="cancelRename"
      />

      <!-- 启用/禁用 toggle -->
      <a-switch
        :checked="node.enabled"
        size="small"
        class="enable-switch"
        @change="emit('toggleEnabled', node.id)"
      />

      <!-- 操作按钮 -->
      <div class="node-actions">
        <a-dropdown v-if="isFolder" :trigger="['click']">
          <a-button type="text" size="small" class="action-btn">
            <template #icon><plus-outlined /></template>
          </a-button>
          <template #overlay>
            <a-menu>
              <a-sub-menu :title="t('skillTreeEditor.addSkillLabel')">
                <a-menu-item
                  v-for="skill in availableSkills"
                  :key="skill.id"
                  @click="emit('addSkillUnder', node.id, skill)"
                >
                  <thunderbolt-outlined />
                  {{ skill.name }}
                </a-menu-item>
                <a-menu-item v-if="availableSkills.length === 0" disabled>
                  {{ t('skillTreeEditor.noAvailableSkill') }}
                </a-menu-item>
              </a-sub-menu>
              <a-menu-item @click="emit('addFolderUnder', node.id)">
                <folder-add-outlined /> {{ t('skillTreeEditor.addSubFolder') }}
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <a-button type="text" size="small" class="action-btn" @click="startRename">
          <template #icon><edit-outlined /></template>
        </a-button>
        <a-popconfirm
          :title="t('skillTreeEditor.deleteNodeConfirm')"
          :ok-text="t('skillTreeEditor.deleteOk')"
          :cancel-text="t('skillTreeEditor.deleteCancel')"
          placement="right"
          @confirm="emit('remove', node.id)"
        >
          <a-button type="text" size="small" danger class="action-btn action-btn-danger">
            <template #icon><delete-outlined /></template>
          </a-button>
        </a-popconfirm>
      </div>
    </div>

    <!-- 下方插入线 -->
    <div
      v-if="dragOverId === node.id && dropPosition === 'after'"
      class="drop-line drop-line-after"
    />

    <!-- 子节点（递归）-->
    <div v-if="isFolder && !folded && (node.children?.length ?? 0) > 0" class="children">
      <SkillTreeNodeItem
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :all-skills="allSkills"
        :dragging-id="draggingId"
        :drag-over-id="dragOverId"
        :drop-position="dropPosition"
        @toggle-enabled="(id) => emit('toggleEnabled', id)"
        @remove="(id) => emit('remove', id)"
        @rename="(id, label) => emit('rename', id, label)"
        @add-skill-under="(pid, skill) => emit('addSkillUnder', pid, skill)"
        @add-folder-under="(pid) => emit('addFolderUnder', pid)"
        @drag-start="(id) => emit('dragStart', id)"
        @drag-over="(id, pos) => emit('dragOver', id, pos)"
        @drag-end="() => emit('dragEnd')"
        @drop="(id, pos) => emit('drop', id, pos)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  FolderOutlined,
  FolderOpenOutlined,
  FolderAddOutlined,
  ThunderboltOutlined,
  RightOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue';
import type { AgentSkill } from '@/api/agent-skill.api';
import type { SkillTreeNode } from '../../agent/agent.types';

const { t } = useI18n();

interface Props {
  node: SkillTreeNode;
  allSkills: AgentSkill[];
  draggingId: string | null;
  dragOverId: string | null;
  dropPosition: 'before' | 'after' | 'inside' | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  toggleEnabled: [nodeId: string];
  remove: [nodeId: string];
  rename: [nodeId: string, newLabel: string];
  addSkillUnder: [parentId: string, skill: AgentSkill];
  addFolderUnder: [parentId: string];
  dragStart: [nodeId: string];
  dragOver: [nodeId: string, position: 'before' | 'after' | 'inside'];
  dragEnd: [];
  drop: [targetId: string, position: 'before' | 'after' | 'inside'];
}>();

const isFolder = computed(() => !props.node.skillId);
const folded = ref(false);

const renaming = ref(false);
const renameValue = ref('');
const renameInputRef = ref<HTMLInputElement | null>(null);

function collectAddedSkillIds(nodes: SkillTreeNode[]): Set<string> {
  const ids = new Set<string>();
  function walk(list: SkillTreeNode[]) {
    for (const n of list) {
      if (n.skillId) ids.add(n.skillId);
      if (n.children) walk(n.children);
    }
  }
  walk(nodes);
  return ids;
}

const availableSkills = computed(() => {
  const addedIds = collectAddedSkillIds([props.node]);
  return props.allSkills.filter((s) => !addedIds.has(s.id));
});

async function startRename() {
  renameValue.value = props.node.label;
  renaming.value = true;
  await nextTick();
  renameInputRef.value?.focus();
}

function commitRename() {
  if (renaming.value) {
    renaming.value = false;
    if (renameValue.value.trim() && renameValue.value !== props.node.label) {
      emit('rename', props.node.id, renameValue.value.trim());
    }
  }
}

function cancelRename() {
  renaming.value = false;
}

// ─── 拖拽事件 ────────────────────────────────────────────────────────────────

function onDragStart(e: DragEvent) {
  e.dataTransfer?.setData('text/plain', props.node.id);
  emit('dragStart', props.node.id);
}

function onDragOver(e: DragEvent) {
  e.preventDefault();
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  const y = e.clientY - rect.top;
  const height = rect.height;

  let position: 'before' | 'after' | 'inside';
  if (isFolder.value) {
    if (y < height * 0.25) {
      position = 'before';
    } else if (y > height * 0.75) {
      position = 'after';
    } else {
      position = 'inside';
    }
  } else {
    position = y < height * 0.5 ? 'before' : 'after';
  }
  emit('dragOver', props.node.id, position);
}

function onDragLeave(e: DragEvent) {
  // 离开当前元素时不需要额外处理，由父级状态管理
}

function onDrop(e: DragEvent) {
  e.preventDefault();
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  const y = e.clientY - rect.top;
  const height = rect.height;

  let position: 'before' | 'after' | 'inside';
  if (isFolder.value) {
    if (y < height * 0.25) {
      position = 'before';
    } else if (y > height * 0.75) {
      position = 'after';
    } else {
      position = 'inside';
    }
  } else {
    position = y < height * 0.5 ? 'before' : 'after';
  }
  emit('drop', props.node.id, position);
}

function onDragEnd() {
  emit('dragEnd');
}
</script>

<style scoped lang="less">
.tree-node {
  user-select: none;
  position: relative;

  &.is-dragging > .node-row {
    opacity: 0.4;
  }
}

// ─── 插入线 ───────────────────────────────────────────────────────────────────

.drop-line {
  height: 2px;
  background: linear-gradient(90deg, #3b82f6, transparent);
  border-radius: 1px;
  margin: 0 8px;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    left: -4px;
    top: -3px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #3b82f6;
  }
}

.drop-line-before {
  margin-bottom: 2px;
}

.drop-line-after {
  margin-top: 2px;
}

// ─── 节点行 ───────────────────────────────────────────────────────────────────

.node-row {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px;
  border-radius: 5px;
  cursor: grab;
  transition: background 0.12s;
  border: 1px solid transparent;

  &:hover {
    background: rgba(59, 130, 246, 0.07);
  }

  &:active {
    cursor: grabbing;
  }

  &.node-disabled {
    opacity: 0.35;
  }

  &.drag-over-inside {
    background: rgba(59, 130, 246, 0.12);
    border-color: rgba(59, 130, 246, 0.35);
  }
}

// ─── 折叠图标 ─────────────────────────────────────────────────────────────────

.fold-icon {
  cursor: pointer;
  color: #475569;
  font-size: 10px;
  width: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  :deep(svg) {
    transition: transform 0.18s;
    transform: rotate(0deg);
  }

  .fold-rotated {
    :deep(svg) {
      transform: rotate(90deg);
    }
  }
}

.fold-rotated {
  :deep(svg) {
    transform: rotate(90deg) !important;
  }
}

.leaf-indent {
  width: 14px;
  flex-shrink: 0;
}

// ─── 节点图标 ─────────────────────────────────────────────────────────────────

.node-icon {
  font-size: 13px;
  flex-shrink: 0;
}

.folder-icon {
  color: #f59e0b;
}

.skill-icon {
  color: #60a5fa;
}

// ─── 节点标签 ─────────────────────────────────────────────────────────────────

.node-label {
  flex: 1;
  font-size: 13px;
  color: #cbd5e1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.rename-input {
  flex: 1;
  height: 22px;
  font-size: 12px;

  :deep(.ant-input) {
    background: rgba(15, 23, 42, 0.8) !important;
    border-color: rgba(59, 130, 246, 0.4) !important;
    color: #e2e8f0 !important;
    height: 22px;
    padding: 0 6px;
    font-size: 12px;
  }
}

// ─── 开关 ─────────────────────────────────────────────────────────────────────

.enable-switch {
  flex-shrink: 0;
}

// ─── 操作按钮 ─────────────────────────────────────────────────────────────────

.node-actions {
  display: flex;
  gap: 1px;
  opacity: 0;
  transition: opacity 0.12s;
  flex-shrink: 0;
}

.node-row:hover .node-actions {
  opacity: 1;
}

.action-btn {
  padding: 0 4px;
  height: 22px;
  color: #475569;
  border-radius: 3px;

  &:hover {
    background: rgba(59, 130, 246, 0.12);
    color: #60a5fa;
  }
}

.action-btn-danger {
  &:hover {
    background: rgba(239, 68, 68, 0.1) !important;
    color: #f87171 !important;
  }
}

// ─── 子节点缩进 ───────────────────────────────────────────────────────────────

.children {
  padding-left: 18px;
  border-left: 1px solid rgba(59, 130, 246, 0.1);
  margin-left: 14px;
}
</style>
