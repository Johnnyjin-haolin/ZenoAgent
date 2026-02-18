<template>
  <div class="agent-slide">
    <div class="slide-header">
      <h3 class="slide-title">对话列表</h3>
      <a-button type="primary" size="small" @click="handleNewChat">
        <template #icon>
          <Icon icon="ant-design:plus-outlined" />
        </template>
        新对话
      </a-button>
    </div>

    <div class="slide-content">
      <div
        v-for="(item, index) in conversations"
        :key="item.id"
        class="conversation-item"
        :class="{ active: item.id === activeId, temporary: item.isTemporary }"
        @click="handleSelect(item)"
      >
        <div class="conversation-main">
          <div v-if="!item.isEdit" class="conversation-title">
            <span>{{ item.title }}</span>
            <span v-if="item.isTemporary" class="conversation-temp-tag">未保存</span>
          </div>
          <a-input
            v-else
            v-model:value="item.title"
            size="small"
            @blur="handleTitleBlur(item)"
            @pressEnter="handleTitleBlur(item)"
          />
        </div>

        <div class="conversation-actions">
          <a-tooltip title="编辑">
            <a-button
              v-if="!item.disabled"
              type="text"
              size="small"
              @click.stop="handleEdit(item)"
            >
              <Icon icon="ant-design:edit-outlined" />
            </a-button>
          </a-tooltip>
          <a-tooltip title="删除">
            <a-button
              v-if="!item.disabled"
              type="text"
              size="small"
              danger
              @click.stop="handleDelete(item)"
            >
              <Icon icon="ant-design:delete-outlined" />
            </a-button>
          </a-tooltip>
        </div>
      </div>

      <a-empty
        v-if="conversations.length === 0"
        description="暂无对话"
        :image="Empty.PRESENTED_IMAGE_SIMPLE"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { Icon } from '@/components/Icon';
import { Empty, Modal, message } from 'ant-design-vue';
import { deleteConversation } from '../agent.api';
import type { ConversationInfo } from '../agent.types';

type ConversationView = ConversationInfo & { isTemporary?: boolean };

const props = defineProps<{
  conversations: ConversationView[];
  activeId?: string;
}>();

const emit = defineEmits<{
  (e: 'select', conversation: ConversationInfo): void;
  (e: 'new'): void;
  (e: 'update', conversation: ConversationInfo): void;
  (e: 'delete', conversationId: string): void;
}>();

// 处理选择会话
const handleSelect = (conversation: ConversationInfo) => {
  if (conversation.isEdit) return;
  emit('select', conversation);
};

// 处理新建对话
const handleNewChat = () => {
  emit('new');
};

// 处理编辑标题
const handleEdit = (conversation: ConversationInfo) => {
  conversation.isEdit = true;
};

// 处理标题失焦
const handleTitleBlur = (conversation: ConversationInfo) => {
  conversation.isEdit = false;
  if (conversation.title.trim()) {
    emit('update', conversation);
  }
};

// 处理删除
const handleDelete = (conversation: ConversationInfo) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除对话 "${conversation.title}" 吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        const success = await deleteConversation(conversation.id);
        if (success) {
          message.success('删除成功');
          emit('delete', conversation.id);
        } else {
          message.error('删除失败');
        }
      } catch (error) {
        message.error('删除失败');
        console.error('删除对话失败:', error);
      }
    },
  });
};
</script>

<style scoped lang="less">
.agent-slide {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: transparent;
}

.slide-header {
  padding: 16px;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(59, 130, 246, 0.1);

  .slide-title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: var(--color-text-primary);
    letter-spacing: -0.5px;
  }
}

.slide-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 8px;
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0.0, 0.2, 1);
  min-height: 48px;
  color: var(--color-text-secondary);

  &:hover {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.1);
    transform: translateY(-1px);
  }

  &.active {
    background: rgba(59, 130, 246, 0.15);
    border-color: rgba(59, 130, 246, 0.5);
    box-shadow: 0 0 15px rgba(59, 130, 246, 0.15);
    color: #fff;

    .conversation-title {
      color: #fff;
      font-weight: 500;
    }
  }

  .conversation-main {
    flex: 1;
    min-width: 0;
  }

  .conversation-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    color: inherit;
    
    span:first-child {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .conversation-temp-tag {
    flex-shrink: 0;
    font-size: 10px;
    color: rgba(255, 255, 255, 0.6);
    background: rgba(255, 255, 255, 0.1);
    border-radius: 4px;
    padding: 0 6px;
    border: 1px solid rgba(255, 255, 255, 0.1);
  }

  .conversation-actions {
    display: flex;
    gap: 4px;
    opacity: 0;
    transition: opacity 0.2s;
    margin-left: 8px;
    
    :deep(.ant-btn) {
      color: var(--color-text-secondary);
      &:hover {
        color: var(--color-text-primary);
        background: rgba(255, 255, 255, 0.1);
      }
      &.ant-btn-dangerous:hover {
        color: #ff4d4f;
        background: rgba(255, 77, 79, 0.1);
      }
    }
  }

  &:hover .conversation-actions {
    opacity: 1;
  }
}

/* Scrollbar */
.slide-content::-webkit-scrollbar {
  width: 4px;
}
.slide-content::-webkit-scrollbar-track {
  background: transparent;
}
.slide-content::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
  &:hover {
    background: rgba(255, 255, 255, 0.2);
  }
}
</style>

