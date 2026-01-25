<template>
  <a-drawer
    v-model:open="drawerOpen"
    title="Agent 配置"
    :width="400"
    placement="right"
  >
    <div class="config-content">
      <div class="config-item">
        <AgentModelSelector v-model="modelId" @change="handleModelChange" />
      </div>

      <div class="config-item">
        <AgentKnowledgeSelector v-model="knowledgeIds" @change="handleKnowledgeChange" />
      </div>

      <div class="config-item">
        <AgentToolConfig v-model="tools" @change="handleToolsChange" />
      </div>

      <div class="config-item">
        <div class="config-label">
          <Icon icon="ant-design:control-outlined" />
          <span>执行模式</span>
        </div>
        <a-radio-group v-model:value="mode">
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
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Icon } from '@/components/Icon';
import AgentKnowledgeSelector from './AgentKnowledgeSelector.vue';
import AgentModelSelector from './AgentModelSelector.vue';
import AgentToolConfig from './AgentToolConfig.vue';
import type { KnowledgeInfo, ModelInfo } from '../agent.types';

const props = defineProps<{
  open: boolean;
  selectedModelId: string;
  selectedKnowledgeIds: string[];
  selectedTools: string[];
  executionMode: 'AUTO' | 'MANUAL';
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'update:selectedModelId', value: string): void;
  (e: 'update:selectedKnowledgeIds', value: string[]): void;
  (e: 'update:selectedTools', value: string[]): void;
  (e: 'update:executionMode', value: 'AUTO' | 'MANUAL'): void;
  (e: 'model-change', modelId: string, model: ModelInfo | null): void;
  (e: 'knowledge-change', knowledgeIds: string[], list: KnowledgeInfo[]): void;
  (e: 'tools-change', tools: string[]): void;
}>();

const drawerOpen = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
});

const modelId = computed({
  get: () => props.selectedModelId,
  set: (value: string) => emit('update:selectedModelId', value),
});

const knowledgeIds = computed({
  get: () => props.selectedKnowledgeIds,
  set: (value: string[]) => emit('update:selectedKnowledgeIds', value),
});

const tools = computed({
  get: () => props.selectedTools,
  set: (value: string[]) => emit('update:selectedTools', value),
});

const mode = computed({
  get: () => props.executionMode,
  set: (value: 'AUTO' | 'MANUAL') => emit('update:executionMode', value),
});

const handleModelChange = (modelIdValue: string, model: ModelInfo | null) => {
  emit('model-change', modelIdValue, model);
};

const handleKnowledgeChange = (ids: string[], list: KnowledgeInfo[]) => {
  emit('knowledge-change', ids, list);
};

const handleToolsChange = (toolNames: string[]) => {
  emit('tools-change', toolNames);
};
</script>

<style scoped lang="less">
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


