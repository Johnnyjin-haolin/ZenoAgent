<template>
  <div class="chat-footer">
<!--    <div v-if="currentStatus" class="status-bar">-->
<!--      <a-spin size="small" />-->
<!--      <span>{{ currentStatus }}</span>-->
<!--    </div>-->

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
            :placeholder="t('agent.selectModel')"
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
            :title="t('agent.stopGeneration')"
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
            :title="t('agent.sendMessage')"
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
import { useI18n } from 'vue-i18n';
import { Icon } from '@/components/Icon';
import AgentModelSelector from './AgentModelSelector.vue';
import type { ModelInfo } from '../agent.types';

const { t } = useI18n();

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
  position: relative;
  z-index: 10;

  .status-bar {
    padding: 6px 12px;
    background: transparent;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: rgba(59, 130, 246, 0.7);
    margin-bottom: 8px;
    font-family: 'JetBrains Mono', monospace;
    
    :deep(.ant-spin) {
      color: #3B82F6;
    }
  }

  .input-container {
    padding: 0;
    max-width: 900px;
    margin: 0 auto;
    width: 100%;
  }

  .input-wrapper {
    position: relative;
    background: rgba(15, 23, 42, 0.6);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    border: 1px solid rgba(59, 130, 246, 0.3);
    transition: all 0.3s cubic-bezier(0.4, 0.0, 0.2, 1);
    padding: 16px 20px;
    min-height: 60px;
    display: flex;
    flex-direction: column;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);

    &:focus-within {
      background: rgba(15, 23, 42, 0.8);
      border-color: rgba(59, 130, 246, 0.6);
      box-shadow: 0 0 20px rgba(59, 130, 246, 0.2);
    }
    
    &::before {
      content: '';
      position: absolute;
      top: -1px;
      left: 20px;
      right: 20px;
      height: 1px;
      background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.5), transparent);
      opacity: 0;
      transition: opacity 0.3s ease;
    }
    
    &:focus-within::before {
      opacity: 1;
    }

    :deep(.ant-input) {
      background: transparent;
      border: none;
      box-shadow: none;
      padding: 0;
      resize: none;
      font-size: 15px;
      line-height: 1.6;
      min-height: 24px;
      margin-bottom: 36px;
      color: #e2e8f0;
      font-family: 'Inter', sans-serif;

      &:focus,
      &:hover {
        border-color: transparent;
        box-shadow: none;
      }

      &::placeholder {
        color: rgba(148, 163, 184, 0.5);
      }
    }

    .input-bottom-left {
      position: absolute;
      bottom: 12px;
      left: 20px;
      z-index: 10;

      :deep(.agent-model-selector) {
        .ant-select {
          .ant-select-selector {
            background: rgba(59, 130, 246, 0.1) !important;
            border: 1px solid rgba(59, 130, 246, 0.2) !important;
            box-shadow: none !important;
            padding: 0 12px;
            border-radius: 6px;
            height: 28px; // 保持紧凑高度
            display: flex;
            align-items: center;
            transition: all 0.2s ease;
          }

          .ant-select-selection-item {
            padding: 0;
            line-height: 26px; // 匹配高度 (28px - 2px border)
            font-size: 12px;
            color: #93c5fd;
            font-weight: 500;
            font-family: 'JetBrains Mono', monospace;
            position: static !important; // 确保不飘飞
          }

          .ant-select-arrow {
            right: 8px;
            font-size: 10px;
            color: #60A5FA;
          }

          &:hover .ant-select-selector {
            background: rgba(59, 130, 246, 0.2) !important;
            border-color: rgba(59, 130, 246, 0.4) !important;
          }
        }
      }
    }

    .input-bottom-right {
      position: absolute;
      bottom: 12px;
      right: 16px;
      z-index: 10;

      .action-button {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 36px;
        height: 36px;
        padding: 0;
        border: none;
        border-radius: 8px;
        transition: all 0.2s;
        cursor: pointer;
        background: transparent;
        color: rgba(148, 163, 184, 0.6);

        &:hover:not(:disabled) {
          background: rgba(255, 255, 255, 0.05);
          color: #e2e8f0;
        }

        &.send-button {
          &:not(:disabled) {
            background: rgba(59, 130, 246, 0.2);
            color: #60A5FA;
            border: 1px solid rgba(59, 130, 246, 0.3);

            &:hover {
              background: rgba(59, 130, 246, 0.4);
              box-shadow: 0 0 10px rgba(59, 130, 246, 0.3);
              transform: translateY(-1px);
            }
            
            &:active {
              transform: translateY(0);
            }
          }

          &:disabled {
            color: rgba(148, 163, 184, 0.2);
            background: transparent;
            border: 1px solid rgba(148, 163, 184, 0.1);
            cursor: not-allowed;
          }
        }
      }
    }
  }
}
</style>


