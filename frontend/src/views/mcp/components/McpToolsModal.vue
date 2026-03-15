<template>
  <a-modal
    :open="open"
    :title="`工具列表 — ${server?.name || ''}`"
    width="680"
    class="mcp-tools-modal"
    :footer="null"
    @cancel="emit('update:open', false)"
  >
    <div v-if="loading" class="modal-loading">
      <a-spin />
      <span>正在获取工具列表...</span>
    </div>

    <div v-else-if="tools.length === 0" class="modal-empty">
      <a-empty description="暂无工具（请检查服务器是否可用）" />
    </div>

    <div v-else class="tools-list">
      <div v-for="tool in tools" :key="tool.name" class="tool-item">
        <div class="tool-header">
          <tool-outlined class="tool-icon" />
          <span class="tool-name">{{ tool.name }}</span>
          <a-tag v-if="tool.enabled" color="green" size="small">启用</a-tag>
          <a-tag v-else color="default" size="small">禁用</a-tag>
        </div>
        <div v-if="tool.description" class="tool-desc">{{ tool.description }}</div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { ToolOutlined } from '@ant-design/icons-vue';
import { getMcpServerTools } from '../../agent/agent.api';
import type { McpServerInfo, McpToolInfo } from '../../agent/agent.types';

interface Props {
  open: boolean;
  server?: McpServerInfo | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
}>();

const loading = ref(false);
const tools = ref<McpToolInfo[]>([]);

watch(
  () => [props.open, props.server?.id],
  async ([open]) => {
    if (!open || !props.server) {
      tools.value = [];
      return;
    }
    loading.value = true;
    try {
      tools.value = await getMcpServerTools(props.server.id);
    } finally {
      loading.value = false;
    }
  }
);
</script>

<style scoped lang="less">
.modal-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}

.modal-empty {
  padding: 40px;
  display: flex;
  justify-content: center;
}

.tools-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 60vh;
  overflow-y: auto;

  &::-webkit-scrollbar {
    width: 5px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 3px;
  }
}

.tool-item {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(59, 130, 246, 0.12);
  border-radius: 8px;
  padding: 12px 14px;
  transition: border-color 0.2s;

  &:hover {
    border-color: rgba(59, 130, 246, 0.28);
  }
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-icon {
  color: #3b82f6;
  font-size: 13px;
  flex-shrink: 0;
}

.tool-name {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
  font-family: 'JetBrains Mono', monospace;
  flex: 1;
}

.tool-desc {
  font-size: 12px;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
  margin-top: 6px;
  line-height: 1.6;
}
</style>

<style lang="less">
.mcp-tools-modal {
  .ant-modal-content {
    background: rgba(10, 15, 30, 0.98) !important;
    border: 1px solid rgba(59, 130, 246, 0.2);
    border-radius: 12px;
  }

  .ant-modal-header {
    background: transparent !important;
    border-bottom-color: rgba(59, 130, 246, 0.12) !important;
  }

  .ant-modal-title {
    color: #e2e8f0 !important;
    font-size: 15px;
    font-weight: 600;
  }

  .ant-modal-close-x {
    color: #64748b !important;

    &:hover {
      color: #e2e8f0 !important;
    }
  }

  .ant-modal-body {
    padding: 20px;
  }
}
</style>
