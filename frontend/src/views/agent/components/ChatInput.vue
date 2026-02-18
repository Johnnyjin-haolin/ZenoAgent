<template>
  <div class="chat-footer">
    <div v-if="currentStatus" class="status-bar">
      <a-spin size="small" />
      <span>{{ currentStatus }}</span>
    </div>

    <div class="input-container">
      <div class="input-wrapper">
        <a-textarea
          ref="inputRef"
          v-model:value="inputValue"
          :placeholder="inputPlaceholder"
          :rows="4"
          :disabled="loading"
          :bordered="false"
          @pressEnter="handlePressEnter"
        />

        <div class="input-bottom-left">
          <AgentModelSelector
            v-model="modelId"
            :compact="true"
            placeholder="选择模型"
            @change="handleModelChange"
          />
        </div>

        <div class="input-bottom-right">
          <a-button
            v-if="loading"
            type="text"
            danger
            size="small"
            @click="emit('stop')"
            title="停止生成"
            class="action-button"
          >
            <template #icon>
              <Icon icon="ant-design:stop-outlined" />
            </template>
          </a-button>
          <a-button
            v-else
            type="text"
            :disabled="!inputValue.trim()"
            @click="emit('send')"
            title="发送消息"
            class="action-button send-button"
          >
            <template #icon>
              <Icon icon="ant-design:send-outlined" />
            </template>
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { Icon } from '@/components/Icon';
import AgentModelSelector from './AgentModelSelector.vue';
import type { ModelInfo } from '../agent.types';

const props = defineProps<{
  modelValue: string;
  loading: boolean;
  inputPlaceholder: string;
  selectedModelId: string;
  currentStatus: string;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'update:selectedModelId', value: string): void;
  (e: 'model-change', modelId: string, model: ModelInfo | null): void;
  (e: 'send'): void;
  (e: 'stop'): void;
}>();

const inputValue = computed({
  get: () => props.modelValue,
  set: (value: string) => emit('update:modelValue', value),
});

const modelId = computed({
  get: () => props.selectedModelId,
  set: (value: string) => emit('update:selectedModelId', value),
});

const inputRef = ref();

const handlePressEnter = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    emit('send');
  }
};

const handleModelChange = (modelIdValue: string, model: ModelInfo | null) => {
  emit('model-change', modelIdValue, model);
};

const focusInput = () => {
  if (inputRef.value) {
    inputRef.value.focus();
  }
};

defineExpose({ focusInput });
</script>

<style scoped lang="less">
.chat-footer {
  background: transparent;
  padding: 0 24px 24px;
  flex-shrink: 0;

  .status-bar {
    padding: 6px 12px;
    background: transparent;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-bottom: 8px;
  }

  .input-container {
    padding: 0;
    max-width: 800px;
    margin: 0 auto;
    width: 100%;
  }

  .input-wrapper {
    position: relative;
    background: var(--color-surface-hover); /* #F1F3F4 */
    border-radius: 24px; /* More rounded */
    border: 1px solid transparent;
    transition: all 0.2s cubic-bezier(0.4, 0.0, 0.2, 1);
    padding: 12px 16px;
    min-height: 56px;
    display: flex;
    flex-direction: column;

    &:focus-within {
      background: #FFFFFF;
      border-color: rgba(0,0,0,0.1);
      box-shadow: 0 2px 6px rgba(0,0,0,0.1);
    }

    :deep(.ant-input) {
      background: transparent;
      border: none;
      box-shadow: none;
      padding: 0;
      resize: none;
      font-size: 16px;
      line-height: 1.5;
      min-height: 24px;
      margin-bottom: 32px; /* Space for bottom actions */
      color: var(--color-text-primary);

      &:focus,
      &:hover {
        border-color: transparent;
        box-shadow: none;
      }

      &::placeholder {
        color: var(--color-text-secondary);
      }
    }

    .input-bottom-left {
      position: absolute;
      bottom: 8px;
      left: 16px;
      z-index: 10;

      :deep(.agent-model-selector) {
        .ant-select {
          .ant-select-selector {
            background: transparent !important;
            border: none !important;
            box-shadow: none !important;
            padding: 0 20px 0 0;
            min-height: auto;
            height: auto;
          }

          .ant-select-selection-item {
            padding: 0;
            line-height: 1.5;
            font-size: 13px;
            color: var(--color-text-secondary);
            font-weight: 500;
          }

          .ant-select-arrow {
            right: 0;
            font-size: 12px;
            color: var(--color-text-secondary);
          }

          &:hover .ant-select-selection-item {
            color: var(--color-text-primary);
          }
        }
      }
    }

    .input-bottom-right {
      position: absolute;
      bottom: 8px;
      right: 12px;
      z-index: 10;

      .action-button {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 32px;
        height: 32px;
        padding: 0;
        border: none;
        border-radius: 50%;
        transition: all 0.2s;
        cursor: pointer;
        background: transparent;
        color: var(--color-text-secondary);

        &:hover:not(:disabled) {
          background: rgba(0,0,0,0.05);
          color: var(--color-text-primary);
        }

        &.send-button {
          &:not(:disabled) {
            background: var(--google-blue);
            color: #fff;

            &:hover {
              background: var(--google-blue-hover);
              box-shadow: 0 1px 3px rgba(0,0,0,0.2);
            }
          }

          &:disabled {
            color: rgba(0,0,0,0.2);
            background: transparent;
            cursor: default;
          }
        }
      }
    }
  }
}
</style>


