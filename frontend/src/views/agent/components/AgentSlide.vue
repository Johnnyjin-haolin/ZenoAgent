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
        :class="{ active: item.id === activeId }"
        @click="handleSelect(item)"
      >
        <div class="conversation-main">
          <div v-if="!item.isEdit" class="conversation-title">
            {{ item.title }}
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

const props = defineProps<{
  conversations: ConversationInfo[];
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
  background: #fafafa;
}

.slide-header {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .slide-title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: #262626;
  }
}

.slide-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  margin-bottom: 8px;
  background: #fff;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: 2px solid transparent;

  &:hover {
    background: #f5f5f5;
    border-color: #d9d9d9;
  }

  &.active {
    background: #e6f7ff;
    border-color: #1890ff;

    .conversation-title {
      color: #1890ff;
      font-weight: 500;
    }
  }

  .conversation-main {
    flex: 1;
    min-width: 0;
  }

  .conversation-title {
    font-size: 14px;
    color: #262626;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .conversation-actions {
    display: flex;
    gap: 4px;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover .conversation-actions {
    opacity: 1;
  }
}

// 滚动条样式
.slide-content::-webkit-scrollbar {
  width: 6px;
}

.slide-content::-webkit-scrollbar-track {
  background: transparent;
}

.slide-content::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 3px;

  &:hover {
    background: #bfbfbf;
  }
}
</style>

