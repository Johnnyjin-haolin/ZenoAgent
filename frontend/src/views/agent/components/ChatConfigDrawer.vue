<template>
  <a-drawer
    v-model:open="drawerOpen"
    :title="t('agent.configDrawer.title')"
    :width="400"
    placement="right"
    class="tech-drawer"
    root-class-name="tech-drawer"
    :headerStyle="{ background: 'transparent', borderBottom: '1px solid rgba(59, 130, 246, 0.2)', color: '#fff' }"
    :bodyStyle="{ background: 'transparent', padding: '24px' }"
    :maskStyle="{ background: 'rgba(0, 0, 0, 0.5)', backdropFilter: 'blur(4px)' }"
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
          <Icon icon="ant-design:control-outlined" class="label-icon" />
          <span>{{ t('agent.configDrawer.executionMode') }}</span>
        </div>
        <a-radio-group v-model:value="mode" class="tech-radio-group">
          <a-radio value="AUTO" class="tech-radio">{{ t('agent.configDrawer.autoMode') }}</a-radio>
          <a-radio value="MANUAL" class="tech-radio">{{ t('agent.configDrawer.manualMode') }}</a-radio>
        </a-radio-group>
        <div class="config-hint">
          {{ mode === 'AUTO' ? t('agent.configDrawer.autoHint') : t('agent.configDrawer.manualHint') }}
        </div>
      </div>

      <div class="config-tips">
        <Icon icon="ant-design:info-circle-outlined" class="tip-icon" />
        <span>{{ t('agent.configDrawer.configTip') }}</span>
      </div>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { Icon } from '@/components/Icon';
import AgentKnowledgeSelector from './AgentKnowledgeSelector.vue';
import AgentModelSelector from './AgentModelSelector.vue';
import AgentToolConfig from './AgentToolConfig.vue';
import type { KnowledgeInfo, ModelInfo } from '../agent.types';

const { t } = useI18n();

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

function handleModelChange(modelIdValue: string, model: ModelInfo | null) {
  emit('model-change', modelIdValue, model);
}

function handleKnowledgeChange(ids: string[], list: KnowledgeInfo[]) {
  emit('knowledge-change', ids, list);
}

function handleToolsChange(toolNames: string[]) {
  emit('tools-change', toolNames);
}
</script>

<style lang="less">
/* Tech Drawer Styles - Global to support Teleport */
.tech-drawer {
  .ant-drawer-content,
  .ant-drawer-wrapper-body {
    background-color: rgba(15, 23, 42, 0.95) !important;
    backdrop-filter: blur(20px);
  }

  .ant-drawer-header {
    background: transparent;
    border-bottom: 1px solid rgba(59, 130, 246, 0.2);

    .ant-drawer-title {
      color: #60A5FA;
      font-family: 'JetBrains Mono', monospace;
      font-weight: 600;
      letter-spacing: 1px;
    }

    .ant-drawer-close {
      color: rgba(148, 163, 184, 0.8);

      &:hover {
        color: #fff;
      }
    }
  }

  .ant-drawer-body {
    &::-webkit-scrollbar {
      width: 6px;
    }
    &::-webkit-scrollbar-track {
      background: rgba(255, 255, 255, 0.02);
    }
    &::-webkit-scrollbar-thumb {
      background: rgba(59, 130, 246, 0.2);
      border-radius: 3px;
    }
  }
}
</style>

<style scoped lang="less">
.config-content {
  .config-item {
    margin-bottom: 24px;

    .config-label {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 10px;
      font-size: 13px;
      font-family: 'JetBrains Mono', monospace;
      color: #e2e8f0;

      .label-icon {
        color: #60A5FA;
      }
    }

    .config-hint {
      margin-top: 10px;
      padding: 10px 12px;
      background: rgba(59, 130, 246, 0.05);
      border: 1px solid rgba(59, 130, 246, 0.1);
      border-radius: 6px;
      font-size: 12px;
      color: #94a3b8;
      line-height: 1.6;
      font-family: 'Inter', sans-serif;
    }

    .tech-radio-group {
      display: flex;
      gap: 12px;
      width: 100%;

      .tech-radio {
        flex: 1;
        display: flex;
        justify-content: center;
        align-items: center;
        height: 36px;
        border: 1px solid rgba(255, 255, 255, 0.1);
        background: rgba(255, 255, 255, 0.02);
        border-radius: 6px;
        color: #94a3b8;
        font-family: 'JetBrains Mono', monospace;
        font-size: 12px;
        margin-right: 0;
        transition: all 0.3s;

        &:hover {
          border-color: rgba(59, 130, 246, 0.4);
          background: rgba(59, 130, 246, 0.05);
        }

        &.ant-radio-wrapper-checked {
          border-color: #60A5FA;
          background: rgba(59, 130, 246, 0.1);
          color: #fff;
          box-shadow: 0 0 10px rgba(59, 130, 246, 0.2);
        }

        :deep(.ant-radio) {
          display: none;
        }

        :deep(span) {
          padding: 0;
        }
      }
    }
  }

  .config-tips {
    display: flex;
    align-items: flex-start;
    padding: 12px;
    background: rgba(59, 130, 246, 0.05);
    border: 1px solid rgba(59, 130, 246, 0.15);
    border-radius: 6px;
    font-size: 12px;
    color: #94a3b8;
    line-height: 1.5;
    margin-top: 16px;

    .tip-icon {
      margin-right: 8px;
      margin-top: 2px;
      color: #60A5FA;
    }
  }
}
</style>
