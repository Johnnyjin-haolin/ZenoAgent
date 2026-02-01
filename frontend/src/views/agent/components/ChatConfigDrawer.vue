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
          <span>动作执行历史轮数</span>
          <a-tooltip placement="top">
            <template #title>
              显示最近几轮迭代的动作执行记录<br/>
              每轮迭代可能包含多个动作（TOOL_CALL、RAG_RETRIEVE等）<br/>
              帮助AI避免重复调用相同工具<br/>
              建议值：1-5轮
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
        </div>
        <a-input-number
          v-model:value="actionExecutionHistoryCount"
          :min="1"
          :max="5"
          :step="1"
          style="width: 100%;"
          placeholder="默认 2"
        >
          <template #addonAfter>轮</template>
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

      <!-- RAG配置区域 -->
      <div class="config-item" style="margin-top: 32px;">
        <span style="font-size: 13px; font-weight: 500; color: #595959;">
          <Icon icon="ant-design:database-outlined" style="margin-right: 4px;" />
          知识库检索配置（RAG）
        </span>
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>最大检索文档数</span>
          <a-tooltip placement="top">
            <template #title>
              从知识库检索的最大文档数量<br/>
              数量越多信息越全，但Token消耗越大<br/>
              建议值：2-5个
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
        </div>
        <a-input-number
          v-model:value="ragMaxResults"
          :min="1"
          :max="20"
          :step="1"
          style="width: 100%;"
          placeholder="默认 3"
        >
          <template #addonAfter>个</template>
        </a-input-number>
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>最小相似度阈值</span>
          <a-tooltip placement="top">
            <template #title>
              只返回相似度高于此值的文档<br/>
              范围：0-1，越高越严格<br/>
              建议值：0.3-0.7
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
        />
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>限制单文档长度</span>
          <a-tooltip placement="top">
            <template #title>
              开启后会截断过长的单个文档<br/>
              关闭则不限制单文档长度<br/>
              适用于大上下文窗口模型
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
          <a-switch 
            v-model:checked="enableDocLengthLimit" 
            size="small"
            style="margin-left: auto;"
          />
        </div>
        <a-input-number
          v-if="enableDocLengthLimit"
          v-model:value="ragMaxDocumentLength"
          :min="200"
          :max="10000"
          :step="100"
          style="width: 100%; margin-top: 8px;"
          placeholder="默认 1000"
        >
          <template #addonAfter>字符</template>
        </a-input-number>
        <div v-else class="config-hint" style="margin-top: 8px;">
          <Icon icon="ant-design:info-circle-outlined" style="color: #52c41a; margin-right: 4px;" />
          已关闭单文档长度限制，将保留完整内容
        </div>
      </div>

      <div class="config-item">
        <div class="config-label">
          <span>限制总内容长度</span>
          <a-tooltip placement="top">
            <template #title>
              开启后会限制所有文档的总长度<br/>
              关闭则不限制，适用于大模型<br/>
              注意：关闭可能导致Token超限
            </template>
            <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
          </a-tooltip>
          <a-switch 
            v-model:checked="enableTotalLengthLimit" 
            size="small"
            style="margin-left: auto;"
          />
        </div>
        <a-input-number
          v-if="enableTotalLengthLimit"
          v-model:value="ragMaxTotalContentLength"
          :min="500"
          :max="50000"
          :step="500"
          style="width: 100%; margin-top: 8px;"
          placeholder="默认 3000"
        >
          <template #addonAfter>字符</template>
        </a-input-number>
        <div v-else class="config-hint" style="margin-top: 8px;">
          <Icon icon="ant-design:info-circle-outlined" style="color: #faad14; margin-right: 4px;" />
          已关闭总长度限制，请确保模型支持大上下文
        </div>
      </div>

      <div class="config-tips">
        <Icon icon="ant-design:bulb-outlined" style="color: #faad14; margin-right: 4px;" />
        <span>提示：如使用Claude 200k、GPT-4 128k等大窗口模型，可关闭长度限制以获取完整知识</span>
      </div>

      <a-button 
        type="link" 
        size="small" 
        @click="resetRagConfig"
        style="margin-top: 8px; padding-left: 0;"
      >
        <Icon icon="ant-design:undo-outlined" />
        重置RAG配置
      </a-button>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Icon } from '@/components/Icon';
import AgentKnowledgeSelector from './AgentKnowledgeSelector.vue';
import AgentModelSelector from './AgentModelSelector.vue';
import AgentToolConfig from './AgentToolConfig.vue';
import type { KnowledgeInfo, ModelInfo, ThinkingConfig, RAGConfig } from '../agent.types';
import { DEFAULT_THINKING_CONFIG, DEFAULT_RAG_CONFIG } from '../agent.types';

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


