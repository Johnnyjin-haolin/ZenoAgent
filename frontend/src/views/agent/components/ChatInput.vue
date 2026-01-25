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
  background: #fff;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;

  .status-bar {
    padding: 6px 20px;
    background: #f0f2f5;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: #595959;
  }

  .input-container {
    padding: 12px 20px;
  }

  .input-wrapper {
    position: relative;
    background: #f5f5f5;
    border-radius: 12px;
    border: 1px solid #e8e8e8;
    transition: all 0.2s;
    padding: 12px;
    min-height: 120px;

    &:focus-within {
      border-color: #1890ff;
      background: #fff;
      box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
    }

    :deep(.ant-input) {
      background: transparent;
      border: none;
      box-shadow: none;
      padding: 0;
      padding-bottom: 40px;
      resize: none;
      font-size: 14px;
      line-height: 1.6;
      min-height: 80px;

      &:focus,
      &:hover {
        border-color: transparent;
        box-shadow: none;
      }

      &::placeholder {
        color: #bfbfbf;
      }
    }

    .input-bottom-left {
      position: absolute;
      bottom: 8px;
      left: 12px;
      z-index: 10;

      :deep(.agent-model-selector) {
        .ant-select {
          .ant-select-selector {
            background: transparent;
            border: none;
            box-shadow: none;
            padding: 0 20px 0 0;
            min-height: auto;
            height: auto;
          }

          .ant-select-selection-item {
            padding: 0;
            line-height: 1.5;
            font-size: 13px;
            color: #595959;
          }

          .ant-select-arrow {
            right: 0;
            font-size: 12px;
            color: #8c8c8c;
          }

          &:hover .ant-select-selector,
          &.ant-select-focused .ant-select-selector {
            border-color: transparent;
            background: transparent;
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
        border-radius: 6px;
        transition: all 0.2s;

        &:hover:not(:disabled) {
          background: #e6f7ff;
          color: #1890ff;
        }

        &.send-button {
          &:not(:disabled) {
            background: #1890ff;
            color: #fff;

            &:hover {
              background: #40a9ff;
            }
          }

          &:disabled {
            color: #bfbfbf;
            background: transparent;
            cursor: not-allowed;
          }
        }

        .anticon {
          font-size: 16px;
        }
      }
    }
  }
}
</style>


