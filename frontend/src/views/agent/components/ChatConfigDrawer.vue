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

      <div class="config-item">
        <span style="font-size: 13px; font-weight: 500; color: #595959;">
          <Icon icon="ant-design:experiment-outlined" style="margin-right: 4px;" />
          对话配置
        </span>
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>对话历史轮数</span>
          <a-tooltip placement="top">
            <template #title>
              控制AI思考时参考多少轮历史对话<br/>
              轮数越多上下文越丰富，但提示词也越长<br/>
              建议值：2-5轮
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
        </div>
        <a-input-number
          v-model:value="conversationHistoryRounds"
          :min="1"
          :max="10"
          :step="1"
          style="width: 100%;"
          placeholder="默认 3"
        >
          <template #addonAfter>轮</template>
        </a-input-number>
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>消息最大长度</span>
          <a-tooltip placement="top">
            <template #title>
              超过此长度的历史消息会被截断<br/>
              用于控制提示词长度，避免过长<br/>
              建议值：100-500字符
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
        </div>
        <a-input-number
          v-model:value="maxMessageLength"
          :min="50"
          :max="1000"
          :step="50"
          style="width: 100%;"
          placeholder="默认 200"
        >
          <template #addonAfter>字符</template>
        </a-input-number>
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>工具调用历史数量</span>
          <a-tooltip placement="top">
            <template #title>
              显示最近几次工具调用记录<br/>
              帮助AI避免重复调用相同工具<br/>
              建议值：1-5次
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
        </div>
        <a-input-number
          v-model:value="toolCallHistoryCount"
          :min="1"
          :max="5"
          :step="1"
          style="width: 100%;"
          placeholder="默认 2"
        >
          <template #addonAfter>次</template>
        </a-input-number>
      </div>

      <div class="config-tips">
        <Icon icon="ant-design:info-circle-outlined" style="color: #1890ff; margin-right: 4px;" />
        <span>这些配置会影响AI的思考质量和响应速度，建议根据实际场景调整</span>
      </div>

      <a-button 
        type="link" 
        size="small" 
        @click="resetThinkingConfig"
        style="margin-top: 8px; padding-left: 0;"
      >
        <Icon icon="ant-design:undo-outlined" />
        重置为默认值
      </a-button>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Icon } from '@/components/Icon';
import AgentKnowledgeSelector from './AgentKnowledgeSelector.vue';
import AgentModelSelector from './AgentModelSelector.vue';
import AgentToolConfig from './AgentToolConfig.vue';
import type { KnowledgeInfo, ModelInfo, ThinkingConfig } from '../agent.types';
import { DEFAULT_THINKING_CONFIG } from '../agent.types';

const props = defineProps<{
  open: boolean;
  selectedModelId: string;
  selectedKnowledgeIds: string[];
  selectedTools: string[];
  executionMode: 'AUTO' | 'MANUAL';
  thinkingConfig: ThinkingConfig;
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'update:selectedModelId', value: string): void;
  (e: 'update:selectedKnowledgeIds', value: string[]): void;
  (e: 'update:selectedTools', value: string[]): void;
  (e: 'update:executionMode', value: 'AUTO' | 'MANUAL'): void;
  (e: 'update:thinkingConfig', value: ThinkingConfig): void;
  (e: 'model-change', modelId: string, model: ModelInfo | null): void;
  (e: 'knowledge-change', knowledgeIds: string[], list: KnowledgeInfo[]): void;
  (e: 'tools-change', tools: string[]): void;
  (e: 'thinking-config-change', config: ThinkingConfig): void;
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

const toolCallHistoryCount = computed({
  get: () => props.thinkingConfig.toolCallHistoryCount ?? DEFAULT_THINKING_CONFIG.toolCallHistoryCount,
  set: (value: number | undefined) => {
    emit('update:thinkingConfig', { ...props.thinkingConfig, toolCallHistoryCount: value });
    emit('thinking-config-change', { ...props.thinkingConfig, toolCallHistoryCount: value });
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

      .help-icon {
        margin-left: 4px;
        color: #999;
        cursor: help;
        font-size: 13px;

        &:hover {
          color: #1890ff;
        }
      }
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

  .config-tips {
    display: flex;
    align-items: flex-start;
    padding: 12px;
    background: #f6f8fa;
    border-radius: 6px;
    font-size: 12px;
    color: #666;
    line-height: 1.5;
    margin-top: 16px;
  }
}
</style>


