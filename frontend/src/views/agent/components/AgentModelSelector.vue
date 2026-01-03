<template>
  <div class="agent-model-selector" :class="{ compact }">
    <div v-if="!compact" class="selector-label">
      <Icon icon="ant-design:api-outlined" />
      <span>AI 模型</span>
    </div>
    
    <!-- 下拉单选框 -->
    <a-select
      v-model:value="selectedModel"
      :placeholder="placeholder"
      :loading="loading"
      :allow-clear="allowClear"
      size="small"
      class="model-select"
      :dropdown-style="{ minWidth: '140px' }"
      @change="handleSelectChange"
    >
      <!-- 智能选择选项 -->
      <a-select-option value="">
        <a-tooltip placement="right" :title="'根据任务类型自动选择最优模型'">
          <div class="select-option-content">
            <span class="option-text">智能选择</span>
          </div>
        </a-tooltip>
      </a-select-option>

      <!-- 模型列表选项 -->
      <a-select-option
        v-for="model in models"
        :key="model.id"
        :value="model.id"
      >
        <a-tooltip placement="right" :title="model.description || model.displayName">
          <div class="select-option-content">
            <span class="option-text">{{ model.displayName }}</span>
          </div>
        </a-tooltip>
      </a-select-option>
    </a-select>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { Icon } from '@/components/Icon';
import { message } from 'ant-design-vue';
import { getAvailableModels } from '../agent.api';
import type { ModelInfo } from '../agent.types';

const props = withDefaults(
  defineProps<{
    modelValue?: string;
    placeholder?: string;
    allowClear?: boolean;
    compact?: boolean; // 紧凑模式（不显示label，适合放在输入框旁边）
  }>(),
  {
    placeholder: '智能选择',
    allowClear: false,
    compact: false,
  }
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'change', value: string, model: ModelInfo | null): void;
}>();

const selectedModel = ref(props.modelValue || '');
const models = ref<ModelInfo[]>([]);
const loading = ref(false);

// 监听外部值变化
watch(
  () => props.modelValue,
  (newValue) => {
    selectedModel.value = newValue || '';
  }
);

// 加载模型列表
const loadModels = async () => {
  loading.value = true;
  try {
    const result = await getAvailableModels();
    
    // 按 sort 排序
    models.value = result.sort((a, b) => a.sort - b.sort);
    
    // 如果没有选择模型，默认选择"智能选择"（空值）
    if (!selectedModel.value) {
      selectedModel.value = '';
      emit('update:modelValue', '');
      emit('change', '', null);
    }
  } catch (error) {
    message.error('加载模型列表失败');
    console.error('加载模型列表失败:', error);
  } finally {
    loading.value = false;
  }
};

// 处理选择框变化
const handleSelectChange = (value: string) => {
  emit('update:modelValue', value);
  
  const selectedModelInfo = models.value.find((m) => m.id === value) || null;
  emit('change', value, selectedModelInfo);
};

onMounted(() => {
  loadModels();
});

// 暴露方法
defineExpose({
  loadModels,
});
</script>

<style scoped lang="less">
.agent-model-selector {
  &.compact {
    // 紧凑模式：无label，直接显示选择器
  }

  .selector-label {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 500;
    color: #262626;
  }

  &.compact .selector-label {
    display: none;
  }

  // 下拉选择框样式
  .model-select {
    width: 100%;
    max-width: 130px;
    min-width: 100px;
    font-size: 12px;

    :deep(.ant-select-selector) {
      border-radius: 6px;
      font-size: 12px;
      display: flex;
      align-items: center;
    }

    // 选中后显示的文本
    :deep(.ant-select-selection-item) {
      font-size: 14px;
      font-weight: 500;
      line-height: 1.5;
      display: flex;
      align-items: center;
    }

    // 下拉箭头图标
    :deep(.ant-select-arrow) {
      display: flex;
      align-items: center;
    }
  }

  // 选项内容样式
  .select-option-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
    width: 100%;
    min-width: 100px;

    .option-text {
      flex: 1;
      font-size: 15px;
      color: #262626;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .option-tag {
      margin: 0;
      font-size: 10px;
      padding: 1px 5px;
      line-height: 14px;
      height: 16px;
      flex-shrink: 0;
      border-radius: 3px;
    }
  }

  // 紧凑模式下的特殊样式
  &.compact {
    .model-select {
      max-width: 100px;
      min-width: 85px;
      font-size: 15px;

      :deep(.ant-select-selector) {
        font-size: 15px;
      }

      :deep(.ant-select-selection-item) {
        font-size: 15px;
      }
    }

    .select-option-content {
      min-width: 85px;
    }
  }
}

// 全局样式：下拉选项样式优化
:deep(.ant-select-dropdown) {
  .ant-select-item {
    padding: 6px 10px;
    font-size: 15px;

    &:hover {
      background: #f5f5f5;
    }

    &.ant-select-item-option-selected {
      background: #e6f7ff;
      font-weight: 600;

      .option-text {
        color: #1890ff;
        font-size: 20px;
        font-weight: 600;
      }
    }
  }
}
</style>

