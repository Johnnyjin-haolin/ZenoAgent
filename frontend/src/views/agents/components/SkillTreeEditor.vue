<template>
  <div class="skill-tree-editor">
    <div class="editor-layout">
      <!-- 左侧：全局 Skill 选择面板 -->
      <div class="skill-pool">
        <div class="panel-title">
          <thunderbolt-outlined class="panel-icon" />
          {{ t('skillTreeEditor.globalSkillPool') }}
        </div>
        <a-input
          v-model:value="searchKeyword"
          :placeholder="t('skillTreeEditor.searchPlaceholder')"
          size="small"
          class="search-input"
          allow-clear
        >
          <template #prefix><search-outlined class="search-icon" /></template>
        </a-input>
        <div class="pool-list">
          <div
            v-for="skill in filteredSkills"
            :key="skill.id"
            class="pool-item"
            :class="{ 'already-added': isAlreadyAdded(skill.id) }"
          >
            <div class="pool-item-info">
              <div class="pool-item-name">{{ skill.name }}</div>
              <div class="pool-item-summary">{{ skill.summary }}</div>
              <div class="pool-item-tags">
                <span v-for="tag in skill.tags" :key="tag" class="pool-tag">{{ tag }}</span>
              </div>
            </div>
            <div v-if="!isAlreadyAdded(skill.id)" class="pool-item-actions">
              <a-dropdown :trigger="['click']" overlay-class-name="skill-pool-dropdown">
                <a-button type="link" size="small" class="add-btn">
                  <template #icon><plus-outlined /></template>
                  {{ t('skillTreeEditor.addTo') }}
                  <down-outlined style="font-size: 10px; margin-left: 2px;" />
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item key="root" @click="addSkillToRoot(skill)">
                      <folder-outlined /> {{ t('skillTreeEditor.rootDir') }}
                    </a-menu-item>
                    <a-menu-divider v-if="folderNodes.length > 0" />
                    <a-menu-item
                      v-for="folder in folderNodes"
                      :key="folder.id"
                      @click="addSkillToFolder(folder.id, skill)"
                    >
                      <folder-open-outlined /> {{ folder.path }}
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
            <span v-else class="already-label">{{ t('skillTreeEditor.alreadyAdded') }}</span>
          </div>
          <a-empty
            v-if="filteredSkills.length === 0"
            :image="Empty.PRESENTED_IMAGE_SIMPLE"
            :description="t('skillTreeEditor.noSkill')"
            class="pool-empty"
          />
        </div>
      </div>

      <!-- 右侧：目录树编辑 -->
      <div class="tree-area">
        <div class="panel-title">
          <folder-outlined class="panel-icon" />
          {{ t('skillTreeEditor.treeTitle') }}
          <a-button type="link" size="small" class="add-folder-btn" @click="addFolderToRoot">
            <template #icon><folder-add-outlined /></template>
            {{ t('skillTreeEditor.addFolder') }}
          </a-button>
        </div>
        <div
          class="tree-container"
          @dragover.prevent
          @drop="handleRootDrop"
        >
          <div v-if="internalTree.length === 0" class="tree-empty">
            <thunderbolt-outlined class="tree-empty-icon" />
            <span>{{ t('skillTreeEditor.treeEmpty') }}</span>
          </div>
          <SkillTreeNodeItem
            v-for="(node, index) in internalTree"
            :key="node.id"
            :node="node"
            :all-skills="skills"
            :dragging-id="draggingId"
            :drag-over-id="dragOverId"
            :drop-position="dropPosition"
            @toggle-enabled="handleToggleEnabled"
            @remove="handleRemove"
            @rename="handleRename"
            @add-skill-under="handleAddSkillUnder"
            @add-folder-under="handleAddFolderUnder"
            @drag-start="handleDragStart"
            @drag-over="handleDragOver"
            @drag-end="handleDragEnd"
            @drop="handleDrop"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { Empty } from 'ant-design-vue';
import { useI18n } from 'vue-i18n';
import {
  SearchOutlined,
  FolderAddOutlined,
  FolderOutlined,
  FolderOpenOutlined,
  ThunderboltOutlined,
  PlusOutlined,
  DownOutlined,
} from '@ant-design/icons-vue';
import { getSkillList } from '@/api/agent-skill.api';
import type { AgentSkill } from '@/api/agent-skill.api';
import type { SkillTreeNode } from '../../agent/agent.types';
import SkillTreeNodeItem from './SkillTreeNodeItem.vue';

const { t } = useI18n();

function genId(): string {
  return Math.random().toString(36).slice(2) + Date.now().toString(36);
}

interface FolderOption {
  id: string;
  path: string;
}

interface Props {
  modelValue: SkillTreeNode[];
}

const props = defineProps<Props>();
const emit = defineEmits<{
  'update:modelValue': [tree: SkillTreeNode[]];
}>();

const skills = ref<AgentSkill[]>([]);
const searchKeyword = ref('');
const internalTree = ref<SkillTreeNode[]>([]);

// ─── 拖拽状态 ────────────────────────────────────────────────────────────────

const draggingId = ref<string | null>(null);
const dragOverId = ref<string | null>(null);
const dropPosition = ref<'before' | 'after' | 'inside' | null>(null);

// ─── 过滤 ────────────────────────────────────────────────────────────────────

const filteredSkills = computed(() => {
  const kw = searchKeyword.value.toLowerCase();
  if (!kw) return skills.value;
  return skills.value.filter(
    (s) =>
      s.name.toLowerCase().includes(kw) ||
      s.tags?.some((t) => t.toLowerCase().includes(kw))
  );
});

// ─── 目录节点列表（供左侧添加到指定目录） ──────────────────────────────────────

const folderNodes = computed<FolderOption[]>(() => {
  const result: FolderOption[] = [];
  function walk(nodes: SkillTreeNode[], prefix: string) {
    for (const node of nodes) {
      if (!node.skillId) {
        const path = prefix ? `${prefix} / ${node.label}` : node.label;
        result.push({ id: node.id, path });
        if (node.children) walk(node.children, path);
      }
    }
  }
  walk(internalTree.value, '');
  return result;
});

// ─── 已添加判断 ───────────────────────────────────────────────────────────────

function collectAllSkillIds(nodes: SkillTreeNode[]): Set<string> {
  const result = new Set<string>();
  function walk(list: SkillTreeNode[]) {
    for (const node of list) {
      if (node.skillId) result.add(node.skillId);
      if (node.children) walk(node.children);
    }
  }
  walk(nodes);
  return result;
}

function isAlreadyAdded(skillId: string): boolean {
  return collectAllSkillIds(internalTree.value).has(skillId);
}

// ─── 添加到根 / 指定目录 ────────────────────────────────────────────────────────

function addSkillToRoot(skill: AgentSkill) {
  internalTree.value = [
    ...internalTree.value,
    { id: genId(), label: skill.name, enabled: true, skillId: skill.id, children: [] },
  ];
  emitUpdate();
}

function addSkillToFolder(folderId: string, skill: AgentSkill) {
  addNodeUnder(internalTree.value, folderId, {
    id: genId(),
    label: skill.name,
    enabled: true,
    skillId: skill.id,
    children: [],
  });
  emitUpdate();
}

function addFolderToRoot() {
  internalTree.value = [
    ...internalTree.value,
    { id: genId(), label: t('skillTreeEditor.newFolder'), enabled: true, children: [] },
  ];
  emitUpdate();
}

// ─── Toggle / 删除 / 重命名 ───────────────────────────────────────────────────

function handleToggleEnabled(nodeId: string) {
  toggleNodeEnabled(internalTree.value, nodeId);
  emitUpdate();
}

function toggleNodeEnabled(nodes: SkillTreeNode[], targetId: string): boolean {
  for (const node of nodes) {
    if (node.id === targetId) {
      node.enabled = !node.enabled;
      if (!node.skillId) {
        setAllChildrenEnabled(node.children, node.enabled);
      }
      return true;
    }
    if (node.children && toggleNodeEnabled(node.children, targetId)) {
      return true;
    }
  }
  return false;
}

function setAllChildrenEnabled(nodes: SkillTreeNode[], enabled: boolean) {
  for (const node of nodes) {
    node.enabled = enabled;
    if (node.children) setAllChildrenEnabled(node.children, enabled);
  }
}

function handleRemove(nodeId: string) {
  internalTree.value = removeNode(internalTree.value, nodeId);
  emitUpdate();
}

function removeNode(nodes: SkillTreeNode[], targetId: string): SkillTreeNode[] {
  return nodes
    .filter((n) => n.id !== targetId)
    .map((n) => ({ ...n, children: removeNode(n.children ?? [], targetId) }));
}

function handleRename(nodeId: string, newLabel: string) {
  renameNode(internalTree.value, nodeId, newLabel);
  emitUpdate();
}

function renameNode(nodes: SkillTreeNode[], targetId: string, newLabel: string) {
  for (const node of nodes) {
    if (node.id === targetId) {
      node.label = newLabel;
      return;
    }
    if (node.children) renameNode(node.children, targetId, newLabel);
  }
}

function handleAddSkillUnder(parentId: string, skill: AgentSkill) {
  addNodeUnder(internalTree.value, parentId, {
    id: genId(),
    label: skill.name,
    enabled: true,
    skillId: skill.id,
    children: [],
  });
  emitUpdate();
}

function handleAddFolderUnder(parentId: string) {
  addNodeUnder(internalTree.value, parentId, {
    id: genId(),
    label: t('skillTreeEditor.newFolder'),
    enabled: true,
    children: [],
  });
  emitUpdate();
}

function addNodeUnder(nodes: SkillTreeNode[], parentId: string, newNode: SkillTreeNode): boolean {
  for (const node of nodes) {
    if (node.id === parentId) {
      node.children = [...(node.children ?? []), newNode];
      return true;
    }
    if (node.children && addNodeUnder(node.children, parentId, newNode)) {
      return true;
    }
  }
  return false;
}

// ─── 拖拽逻辑 ────────────────────────────────────────────────────────────────

function handleDragStart(nodeId: string) {
  draggingId.value = nodeId;
}

function handleDragOver(nodeId: string, position: 'before' | 'after' | 'inside') {
  if (nodeId === draggingId.value) return;
  dragOverId.value = nodeId;
  dropPosition.value = position;
}

function handleDragEnd() {
  draggingId.value = null;
  dragOverId.value = null;
  dropPosition.value = null;
}

function handleDrop(targetId: string, position: 'before' | 'after' | 'inside') {
  if (!draggingId.value || draggingId.value === targetId) {
    handleDragEnd();
    return;
  }
  if (isDescendant(internalTree.value, draggingId.value, targetId)) {
    handleDragEnd();
    return;
  }
  const dragNode = extractNode(internalTree.value, draggingId.value);
  if (!dragNode) {
    handleDragEnd();
    return;
  }
  internalTree.value = removeNode(internalTree.value, draggingId.value);
  insertNode(internalTree.value, targetId, dragNode, position);
  handleDragEnd();
  emitUpdate();
}

function handleRootDrop(e: DragEvent) {
  if (!draggingId.value) return;
  const dragNode = extractNode(internalTree.value, draggingId.value);
  if (!dragNode) {
    handleDragEnd();
    return;
  }
  internalTree.value = removeNode(internalTree.value, draggingId.value);
  internalTree.value = [...internalTree.value, dragNode];
  handleDragEnd();
  emitUpdate();
}

function extractNode(nodes: SkillTreeNode[], targetId: string): SkillTreeNode | null {
  for (const node of nodes) {
    if (node.id === targetId) return { ...node };
    if (node.children) {
      const found = extractNode(node.children, targetId);
      if (found) return found;
    }
  }
  return null;
}

function insertNode(
  nodes: SkillTreeNode[],
  targetId: string,
  newNode: SkillTreeNode,
  position: 'before' | 'after' | 'inside'
): boolean {
  for (let i = 0; i < nodes.length; i++) {
    if (nodes[i].id === targetId) {
      if (position === 'before') {
        nodes.splice(i, 0, newNode);
      } else if (position === 'after') {
        nodes.splice(i + 1, 0, newNode);
      } else {
        nodes[i].children = [...(nodes[i].children ?? []), newNode];
      }
      return true;
    }
    if (nodes[i].children && insertNode(nodes[i].children!, targetId, newNode, position)) {
      return true;
    }
  }
  return false;
}

function isDescendant(nodes: SkillTreeNode[], ancestorId: string, targetId: string): boolean {
  for (const node of nodes) {
    if (node.id === ancestorId) {
      return containsNode(node.children ?? [], targetId);
    }
    if (node.children && isDescendant(node.children, ancestorId, targetId)) {
      return true;
    }
  }
  return false;
}

function containsNode(nodes: SkillTreeNode[], targetId: string): boolean {
  for (const node of nodes) {
    if (node.id === targetId) return true;
    if (node.children && containsNode(node.children, targetId)) return true;
  }
  return false;
}

// ─── 同步 & 触发更新 ─────────────────────────────────────────────────────────

function emitUpdate() {
  emit('update:modelValue', JSON.parse(JSON.stringify(internalTree.value)));
}

watch(
  () => props.modelValue,
  (val) => {
    internalTree.value = JSON.parse(JSON.stringify(val || []));
  },
  { immediate: true, deep: true }
);

onMounted(async () => {
  skills.value = await getSkillList();
});
</script>

<style scoped lang="less">
.skill-tree-editor {
  width: 100%;
}

.editor-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 14px;
  min-height: 320px;
}

// ─── 面板通用 ────────────────────────────────────────────────────────────────

.skill-pool,
.tree-area {
  background: rgba(8, 14, 28, 0.5);
  border: 1px solid rgba(59, 130, 246, 0.12);
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel-title {
  font-size: 11px;
  font-weight: 600;
  color: #475569;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  display: flex;
  align-items: center;
  gap: 6px;
}

.panel-icon {
  font-size: 12px;
  color: #3b82f6;
}

.add-folder-btn {
  margin-left: auto;
  font-size: 11px;
  padding: 0;
  height: auto;
  color: #60a5fa;

  &:hover {
    color: #93c5fd;
  }
}

// ─── 搜索框（占位符，实际样式在下方全局 style 块中） ────────────────────

.search-input {
  // 这里仅保留布局相关的属性即可，深色颜色通过全局 style 覆盖
  width: 100%;
}

.search-icon {
  color: #475569;
  font-size: 12px;
}

// ─── Skill 池列表 ─────────────────────────────────────────────────────────────

.pool-list {
  flex: 1;
  overflow-y: auto;
  max-height: 380px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.08);
    border-radius: 2px;
  }
}

.pool-item {
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: 6px;
  padding: 8px 10px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  transition: border-color 0.15s;

  &:hover:not(.already-added) {
    border-color: rgba(59, 130, 246, 0.3);
  }

  &.already-added {
    opacity: 0.35;
  }
}

.pool-item-info {
  flex: 1;
  min-width: 0;
}

.pool-item-name {
  font-size: 12px;
  font-weight: 600;
  color: #cbd5e1;
  margin-bottom: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pool-item-summary {
  font-size: 11px;
  color: #475569;
  line-height: 1.4;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.pool-item-tags {
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}

.pool-tag {
  font-size: 10px;
  padding: 1px 5px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.2);
  color: #60a5fa;
  border-radius: 3px;
  line-height: 1.5;
}

.pool-item-actions {
  flex-shrink: 0;
}

.add-btn {
  font-size: 11px;
  padding: 0 4px;
  height: auto;
  color: #60a5fa;
  display: flex;
  align-items: center;
  gap: 2px;

  &:hover {
    color: #93c5fd;
  }
}

.already-label {
  font-size: 11px;
  color: #334155;
  flex-shrink: 0;
  padding-top: 2px;
}

.pool-empty {
  padding: 20px 0;

  :deep(.ant-empty-description) {
    color: #334155;
    font-size: 12px;
  }
}

// ─── 树区域 ───────────────────────────────────────────────────────────────────

.tree-container {
  flex: 1;
  overflow-y: auto;
  max-height: 380px;
  min-height: 100px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.08);
    border-radius: 2px;
  }
}

.tree-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: #334155;
  font-size: 12px;
}

.tree-empty-icon {
  font-size: 24px;
  color: #1e3a5f;
}
</style>

<style lang="less">
// 全局样式块（无 scoped），用 .skill-tree-editor 命名空间隔离
// 专门覆盖 ant-input-affix-wrapper 深色主题
// 原因：scoped + :deep 对 allow-clear 场景的内部 input 覆盖不可靠
.skill-tree-editor {
  // .search-input 本身就是 ant-input-affix-wrapper 根元素，用组合选择器直接匹配
  .search-input.ant-input-affix-wrapper,
  .search-input .ant-input-affix-wrapper {
    background: rgba(15, 23, 42, 0.6) !important;
    border-color: rgba(59, 130, 246, 0.15) !important;
    box-shadow: none !important;

    &:hover,
    &:focus-within,
    &.ant-input-affix-wrapper-focused {
      border-color: rgba(59, 130, 246, 0.4) !important;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.08) !important;
    }

    // 内部真实 input 元素
    input.ant-input {
      background: transparent !important;
      color: #cbd5e1 !important;
      font-size: 12px;

      &::placeholder {
        color: #334155 !important;
      }
    }

    // prefix 搜索图标区域
    .ant-input-prefix {
      background: transparent !important;
      color: #475569;
      margin-inline-end: 6px;
    }

    // clear 按钮
    .ant-input-clear-icon {
      color: #475569 !important;
      background: transparent !important;
      &:hover {
        color: #94a3b8 !important;
      }
      svg {
        background: transparent !important;
      }
    }

    // anticon 通用（search、close-circle 等）
    .anticon {
      background: transparent !important;
      color: #475569;
    }
  }
}
</style>
