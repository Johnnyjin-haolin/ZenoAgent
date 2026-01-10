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
      :dropdown-match-select-width="false"
      dropdown-class-name="model-select-dropdown"
      @change="handleSelectChange"
    >
      <!-- 智能选择选项 -->
      <a-select-option value="">
        <a-tooltip placement="right" :title="'根据任务类型自动选择最优模型'">
          <div class="select-option-content">
            <div class="option-main">
              <span class="option-name">智能选择</span>
            </div>
          </div>
        </a-tooltip>
      </a-select-option>

      <!-- 模型列表选项 -->
      <a-select-option
        v-for="model in models"
        :key="model.id"
        :value="model.id"
      >
        <a-tooltip 
          placement="right" 
          :title="model.description"
        >
          <div class="select-option-content">
            <div class="option-main">
              <span class="option-name" :title="model.displayName ">
                {{ model.displayName}}
              </span>
            </div>
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
import { ModelType } from '@/types/model.types';
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

// 加载模型列表（只加载 CHAT 类型的模型）
const loadModels = async () => {
  loading.value = true;
  try {
    const result = await getAvailableModels(ModelType.CHAT);
    
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

// 获取提供商标签
const getProviderLabel = (provider?: string): string => {
  if (!provider) return '';
  
  const providerMap: Record<string, string> = {
    'OPENAI': 'OpenAI',
    'ZHIPU': '智谱AI',
    'DEEPSEEK': 'DeepSeek',
    'QWEN': '通义千问',
    'GLM': 'GLM',
  };
  
  return providerMap[provider.toUpperCase()] || provider;
};

// 获取模型的 Tooltip 内容
const getModelTooltip = (model: ModelInfo): string => {
  const parts: string[] = [];
  
  // 添加模型名称
  if (model.displayName || model.name) {
    parts.push(`模型: ${model.displayName || model.name}`);
  }
  
  // 添加描述
  if (model.description) {
    parts.push(`描述: ${model.description}`);
  }
  
  // 添加提供商
  if (model.provider) {
    parts.push(`提供商: ${getProviderLabel(model.provider)}`);
  }
  
  return parts.join('\n');
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

    // 选中后显示的文本（截断处理）
    :deep(.ant-select-selection-item) {
      font-size: 14px;
      font-weight: 500;
      line-height: 1.5;
      display: flex;
      align-items: center;
      max-width: 110px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
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
    flex-direction: column;
    gap: 4px;
    width: 100%;
    min-width: 200px;
    padding: 2px 0;

    .option-main {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      width: 100%;
    }

    .option-name {
      flex: 1;
      font-size: 14px;
      color: #262626;
      font-weight: 500;
      // 允许换行，但优先单行显示
      word-break: break-word;
      line-height: 1.5;
      min-width: 0; // 允许 flex 收缩
    }

    .option-provider {
      flex-shrink: 0;
      font-size: 11px;
      color: #8c8c8c;
      background: #f0f0f0;
      padding: 2px 6px;
      border-radius: 3px;
      font-weight: normal;
    }

    .option-desc {
      font-size: 12px;
      color: #8c8c8c;
      line-height: 1.4;
      margin-top: 2px;
      // 描述最多显示2行
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      text-overflow: ellipsis;
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
        max-width: 80px; // 紧凑模式下更小的最大宽度
      }
    }

    .select-option-content {
      min-width: 200px; // 下拉选项仍然保持最小宽度以确保内容完整显示
    }
  }
}

// 全局样式：下拉选项样式优化
:deep(.ant-select-dropdown.model-select-dropdown) {
  // 下拉菜单宽度自适应（最小200px，最大350px）
  min-width: 200px !important;
  max-width: 350px !important;
  width: auto !important;

  .ant-select-item {
    padding: 8px 12px;
    font-size: 14px;
    line-height: 1.5;

    &:hover {
      background: #f5f5f5;
    }

    &.ant-select-item-option-selected {
      background: #e6f7ff;
      font-weight: 500;

      .option-name {
        color: #1890ff;
        font-weight: 600;
      }

      .option-provider {
        background: #bae7ff;
        color: #0958d9;
      }
    }
  }
}
</style>

