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

      <div class="section-divider">
        <span class="section-title">
          <Icon icon="ant-design:experiment-outlined" />
          {{ t('agent.configDrawer.dialogueConfig') }}
        </span>
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>{{ t('agent.configDrawer.historyRounds') }}</span>
          <a-tooltip placement="top">
            <template #title>
              <span style="white-space: pre-wrap">{{ t('agent.configDrawer.historyRoundsTooltip') }}</span>
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
        </div>
        <a-input-number
          v-model:value="conversationHistoryRounds"
          :min="1"
          :max="10"
          :step="1"
          class="tech-input-number"
          placeholder="Default 3"
        />
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>{{ t('agent.configDrawer.maxMsgLen') }}</span>
          <a-tooltip placement="top">
            <template #title>
              <span style="white-space: pre-wrap">{{ t('agent.configDrawer.maxMsgLenTooltip') }}</span>
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
        </div>
        <a-input-number
          v-model:value="maxMessageLength"
          :min="50"
          :max="1000"
          :step="50"
          class="tech-input-number"
          placeholder="Default 200"
        />
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>{{ t('agent.configDrawer.actionHistory') }}</span>
          <a-tooltip placement="top">
            <template #title>
              <span style="white-space: pre-wrap">{{ t('agent.configDrawer.actionHistoryTooltip') }}</span>
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
        </div>
        <a-input-number
          v-model:value="actionExecutionHistoryCount"
          :min="1"
          :max="5"
          :step="1"
          class="tech-input-number"
          placeholder="Default 2"
        />
      </div>

      <div class="config-tips">
        <Icon icon="ant-design:info-circle-outlined" class="tip-icon" />
        <span>{{ t('agent.configDrawer.configTip') }}</span>
      </div>

      <a-button 
        type="link" 
        size="small" 
        @click="resetThinkingConfig"
        class="reset-btn"
      >
        <Icon icon="ant-design:undo-outlined" />
        {{ t('agent.configDrawer.resetDefault') }}
      </a-button>

      <!-- RAG配置区域 -->
      <div class="section-divider">
        <span class="section-title">
          <Icon icon="ant-design:database-outlined" />
          {{ t('agent.configDrawer.ragConfig') }}
        </span>
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>{{ t('agent.configDrawer.maxResults') }}</span>
          <a-tooltip placement="top">
            <template #title>
              <span style="white-space: pre-wrap">{{ t('agent.configDrawer.maxResultsTooltip') }}</span>
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
        </div>
        <a-input-number
          v-model:value="ragMaxResults"
          :min="1"
          :max="20"
          :step="1"
          class="tech-input-number"
          placeholder="Default 3"
        />
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>{{ t('agent.configDrawer.minScore') }}</span>
          <a-tooltip placement="top">
            <template #title>
              <span style="white-space: pre-wrap">{{ t('agent.configDrawer.minScoreTooltip') }}</span>
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
        </div>
        <a-slider
          v-model:value="ragMinScore"
          :min="0"
          :max="1"
          :step="0.05"
          :marks="{ 0: '0', 0.5: '0.5', 1: '1' }"
          class="tech-slider"
        />
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>{{ t('agent.configDrawer.docLenLimit') }}</span>
          <a-tooltip placement="top">
            <template #title>
              <span style="white-space: pre-wrap">{{ t('agent.configDrawer.docLenLimitTooltip') }}</span>
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
          <a-switch 
            v-model:checked="enableDocLengthLimit" 
            size="small"
            class="tech-switch"
          />
        </div>
        <a-input-number
          v-if="enableDocLengthLimit"
          v-model:value="ragMaxDocumentLength"
          :min="200"
          :max="10000"
          :step="100"
          class="tech-input-number mt-2"
          placeholder="Default 1000"
        />
        <div v-else class="config-hint success mt-2">
          <Icon icon="ant-design:info-circle-outlined" />
          {{ t('agent.configDrawer.docLenUnlimited') }}
        </div>
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>{{ t('agent.configDrawer.totalLenLimit') }}</span>
          <a-tooltip placement="top">
            <template #title>
              <span style="white-space: pre-wrap">{{ t('agent.configDrawer.totalLenLimitTooltip') }}</span>
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
          <a-switch 
            v-model:checked="enableTotalLengthLimit" 
            size="small"
            class="tech-switch"
          />
        </div>
        <a-input-number
          v-if="enableTotalLengthLimit"
          v-model:value="ragMaxTotalContentLength"
          :min="500"
          :max="50000"
          :step="500"
          class="tech-input-number mt-2"
          placeholder="Default 3000"
        />
        <div v-else class="config-hint warning mt-2">
          <Icon icon="ant-design:info-circle-outlined" />
          {{ t('agent.configDrawer.totalLenUnlimited') }}
        </div>
      </div>

      <div class="config-tips warning">
        <Icon icon="ant-design:bulb-outlined" class="tip-icon" />
        <span>{{ t('agent.configDrawer.largeContextTip') }}</span>
      </div>

      <a-button 
        type="link" 
        size="small" 
        @click="resetRagConfig"
        class="reset-btn"
      >
        <Icon icon="ant-design:undo-outlined" />
        {{ t('agent.configDrawer.resetRag') }}
      </a-button>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Icon } from '@/components/Icon';
import AgentKnowledgeSelector from './AgentKnowledgeSelector.vue';
import AgentModelSelector from './AgentModelSelector.vue';
import AgentToolConfig from './AgentToolConfig.vue';
import type { KnowledgeInfo, ModelInfo, ThinkingConfig, RAGConfig } from '../agent.types';
import { DEFAULT_THINKING_CONFIG, DEFAULT_RAG_CONFIG } from '../agent.types';

const { t } = useI18n();

const props = defineProps<{
  open: boolean;
  selectedModelId: string;
  selectedKnowledgeIds: string[];
  selectedTools: string[];
  executionMode: 'AUTO' | 'MANUAL';
  thinkingConfig: ThinkingConfig;
  ragConfig: RAGConfig;
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'update:selectedModelId', value: string): void;
  (e: 'update:selectedKnowledgeIds', value: string[]): void;
  (e: 'update:selectedTools', value: string[]): void;
  (e: 'update:executionMode', value: 'AUTO' | 'MANUAL'): void;
  (e: 'update:thinkingConfig', value: ThinkingConfig): void;
  (e: 'update:ragConfig', value: RAGConfig): void;
  (e: 'model-change', modelId: string, model: ModelInfo | null): void;
  (e: 'knowledge-change', knowledgeIds: string[], list: KnowledgeInfo[]): void;
  (e: 'tools-change', tools: string[]): void;
  (e: 'thinking-config-change', config: ThinkingConfig): void;
  (e: 'rag-config-change', config: RAGConfig): void;
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

const conversationHistoryRounds = computed({
  get: () => props.thinkingConfig.conversationHistoryRounds ?? DEFAULT_THINKING_CONFIG.conversationHistoryRounds,
  set: (value: number | undefined) => {
    emit('update:thinkingConfig', { ...props.thinkingConfig, conversationHistoryRounds: value });
    emit('thinking-config-change', { ...props.thinkingConfig, conversationHistoryRounds: value });
  },
});

const maxMessageLength = computed({
  get: () => props.thinkingConfig.maxMessageLength ?? DEFAULT_THINKING_CONFIG.maxMessageLength,
  set: (value: number | undefined) => {
    emit('update:thinkingConfig', { ...props.thinkingConfig, maxMessageLength: value });
    emit('thinking-config-change', { ...props.thinkingConfig, maxMessageLength: value });
  },
});

const actionExecutionHistoryCount = computed({
  get: () => props.thinkingConfig.actionExecutionHistoryCount ?? DEFAULT_THINKING_CONFIG.actionExecutionHistoryCount,
  set: (value: number | undefined) => {
    emit('update:thinkingConfig', { ...props.thinkingConfig, actionExecutionHistoryCount: value });
    emit('thinking-config-change', { ...props.thinkingConfig, actionExecutionHistoryCount: value });
  },
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

const resetThinkingConfig = () => {
  const resetConfig = { ...DEFAULT_THINKING_CONFIG };
  emit('update:thinkingConfig', resetConfig);
  emit('thinking-config-change', resetConfig);
};

// RAG配置相关
const enableDocLengthLimit = ref(true);  // 是否启用单文档长度限制
const enableTotalLengthLimit = ref(true);  // 是否启用总长度限制

const ragMaxResults = computed({
  get: () => props.ragConfig?.maxResults ?? DEFAULT_RAG_CONFIG.maxResults,
  set: (value: number | undefined) => {
    const newConfig = { ...props.ragConfig, maxResults: value };
    emit('update:ragConfig', newConfig);
    emit('rag-config-change', newConfig);
  },
});

const ragMinScore = computed({
  get: () => props.ragConfig?.minScore ?? DEFAULT_RAG_CONFIG.minScore,
  set: (value: number | undefined) => {
    const newConfig = { ...props.ragConfig, minScore: value };
    emit('update:ragConfig', newConfig);
    emit('rag-config-change', newConfig);
  },
});

const ragMaxDocumentLength = computed({
  get: () => props.ragConfig?.maxDocumentLength ?? DEFAULT_RAG_CONFIG.maxDocumentLength,
  set: (value: number | undefined | null) => {
    const newConfig = { 
      ...props.ragConfig, 
      maxDocumentLength: enableDocLengthLimit.value ? value : null 
    };
    emit('update:ragConfig', newConfig);
    emit('rag-config-change', newConfig);
  },
});

const ragMaxTotalContentLength = computed({
  get: () => props.ragConfig?.maxTotalContentLength ?? DEFAULT_RAG_CONFIG.maxTotalContentLength,
  set: (value: number | undefined | null) => {
    const newConfig = { 
      ...props.ragConfig, 
      maxTotalContentLength: enableTotalLengthLimit.value ? value : null 
    };
    emit('update:ragConfig', newConfig);
    emit('rag-config-change', newConfig);
  },
});

// 监听开关变化
watch(enableDocLengthLimit, (enabled) => {
  const newConfig = { 
    ...props.ragConfig, 
    maxDocumentLength: enabled ? (ragMaxDocumentLength.value ?? 1000) : null 
  };
  emit('update:ragConfig', newConfig);
  emit('rag-config-change', newConfig);
});

watch(enableTotalLengthLimit, (enabled) => {
  const newConfig = { 
    ...props.ragConfig, 
    maxTotalContentLength: enabled ? (ragMaxTotalContentLength.value ?? 3000) : null 
  };
  emit('update:ragConfig', newConfig);
  emit('rag-config-change', newConfig);
});

// 初始化开关状态
watch(() => props.ragConfig, (config) => {
  if (config) {
    enableDocLengthLimit.value = config.maxDocumentLength != null;
    enableTotalLengthLimit.value = config.maxTotalContentLength != null;
  }
}, { immediate: true });

const resetRagConfig = () => {
  enableDocLengthLimit.value = true;
  enableTotalLengthLimit.value = true;
  const resetConfig = { ...DEFAULT_RAG_CONFIG };
  emit('update:ragConfig', resetConfig);
  emit('rag-config-change', resetConfig);
};
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
  .section-divider {
    margin-top: 32px;
    margin-bottom: 16px;
    padding-bottom: 8px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    
    .section-title {
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
      font-weight: 600;
      color: #94a3b8;
      display: flex;
      align-items: center;
      gap: 8px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
  }

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

      .help-icon {
        color: rgba(148, 163, 184, 0.6);
        cursor: help;
        font-size: 12px;
        transition: color 0.2s;

        &:hover {
          color: #60A5FA;
        }
      }
      
      .tech-switch {
        margin-left: auto;
        background-color: rgba(255, 255, 255, 0.1);
        
        &.ant-switch-checked {
          background-color: #60A5FA;
        }
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
      
      &.success {
        background: rgba(16, 185, 129, 0.05);
        border-color: rgba(16, 185, 129, 0.1);
        color: #34D399;
      }
      
      &.warning {
        background: rgba(245, 158, 11, 0.05);
        border-color: rgba(245, 158, 11, 0.1);
        color: #FBBF24;
      }
      
      .anticon {
        margin-right: 6px;
      }
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
          display: none; /* Hide default radio circle */
        }
        
        :deep(span) {
          padding: 0;
        }
      }
    }
    
    .tech-input-number {
      width: 100%;
      background: rgba(0, 0, 0, 0.2);
      border: 1px solid rgba(59, 130, 246, 0.2);
      border-radius: 4px;
      color: #fff;
      font-family: 'JetBrains Mono', monospace;
      
      &:hover, &:focus, &.ant-input-number-focused {
        border-color: #60A5FA;
        box-shadow: 0 0 8px rgba(59, 130, 246, 0.2);
      }
      
      :deep(.ant-input-number-input) {
        color: #fff;
        height: 34px;
      }
      
      :deep(.ant-input-number-handler-wrap) {
        background: rgba(255, 255, 255, 0.05);
        border-left: 1px solid rgba(59, 130, 246, 0.2);
      }
      
      :deep(.ant-input-number-handler) {
        border-bottom: 1px solid rgba(59, 130, 246, 0.2);
        &:hover .anticon {
          color: #60A5FA;
        }
      }
    }
    
    .mt-2 {
      margin-top: 8px;
    }
    
    .tech-slider {
      :deep(.ant-slider-rail) {
        background-color: rgba(255, 255, 255, 0.1);
      }
      
      :deep(.ant-slider-track) {
        background-color: #60A5FA;
        box-shadow: 0 0 8px rgba(59, 130, 246, 0.5);
      }
      
      :deep(.ant-slider-handle) {
        border-color: #60A5FA;
        background-color: #0f172a;
        box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3);
        
        &:hover, &:focus {
          box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.3);
        }
      }
      
      :deep(.ant-slider-mark-text) {
        color: rgba(148, 163, 184, 0.6);
        font-family: 'JetBrains Mono', monospace;
        font-size: 10px;
        
        &-active {
          color: #60A5FA;
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
    
    &.warning {
      background: rgba(245, 158, 11, 0.05);
      border-color: rgba(245, 158, 11, 0.15);
      
      .tip-icon {
        color: #FBBF24;
      }
    }
  }
  
  .reset-btn {
    margin-top: 12px;
    padding-left: 0;
    color: rgba(148, 163, 184, 0.6);
    font-family: 'JetBrains Mono', monospace;
    font-size: 12px;
    
    &:hover {
      color: #60A5FA;
    }
  }
}
</style>


