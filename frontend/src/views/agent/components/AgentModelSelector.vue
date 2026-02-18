<template>
  <div class="agent-model-selector" :class="{ compact }">
    <div v-if="!compact" class="selector-label">
      <Icon icon="ant-design:api-outlined" class="label-icon" />
      <span>{{ t('agent.modelSelector.label') }}</span>
    </div>
    
    <!-- 下拉单选框 -->
    <a-select
      v-model:value="selectedModel"
      :placeholder="placeholder || t('agent.modelSelector.auto')"
      :loading="loading"
      :allow-clear="allowClear"
      size="small"
      class="model-select tech-select"
      :dropdown-match-select-width="false"
      dropdown-class-name="model-select-dropdown tech-dropdown"
      @change="handleSelectChange"
    >
      <!-- 智能选择选项 -->
      <a-select-option value="">
        <span class="option-name">{{ t('agent.modelSelector.auto') }}</span>
      </a-select-option>

      <!-- 模型列表选项 -->
      <a-select-option
        v-for="model in models"
        :key="model.id"
        :value="model.id"
      >
        <span class="option-name" :title="model.displayName || model.name">
                {{ model.displayName || model.name }}
        </span>
      </a-select-option>
    </a-select>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Icon } from '@/components/Icon';
import { message } from 'ant-design-vue';
import { getAvailableModels } from '../agent.api';
import { ModelType } from '@/types/model.types';
import type { ModelInfo } from '../agent.types';

const { t } = useI18n();

const props = withDefaults(
  defineProps<{
    modelValue?: string;
    placeholder?: string;
    allowClear?: boolean;
    compact?: boolean; // 紧凑模式（不显示label，适合放在输入框旁边）
  }>(),
  {
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
    message.error(t('agent.modelSelector.loadingError'));
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
    parts.push(`${t('agent.modelSelector.model')}: ${model.displayName || model.name}`);
  }
  
  // 添加描述
  if (model.description) {
    parts.push(`${t('agent.modelSelector.desc')}: ${model.description}`);
  }
  
  // 添加提供商
  if (model.provider) {
    parts.push(`${t('agent.modelSelector.provider')}: ${getProviderLabel(model.provider)}`);
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
    gap: 8px;
    margin-bottom: 10px;
    font-size: 13px;
    font-family: 'JetBrains Mono', monospace;
    color: #e2e8f0;

    .label-icon {
      color: #60A5FA;
    }
  }

  &.compact .selector-label {
    display: none;
  }

  // 下拉选择框样式
  .model-select {
    width: 100%;
    // max-width: 130px; // Remove max-width constraint for better fit in drawer
    font-size: 12px;
    font-family: 'JetBrains Mono', monospace;

    :deep(.ant-select-selector) {
      background-color: rgba(0, 0, 0, 0.2) !important;
      border: 1px solid rgba(59, 130, 246, 0.2) !important;
      border-radius: 4px;
      color: #fff !important;
      height: 32px;
      display: flex;
      align-items: center;
      transition: all 0.3s;
      padding: 0 11px !important; // 确保内边距一致
      
      &:hover {
        border-color: #60A5FA !important;
        box-shadow: 0 0 8px rgba(59, 130, 246, 0.2);
      }
    }
    
    :deep(.ant-select-selection-placeholder) {
      color: rgba(148, 163, 184, 0.4);
      line-height: 30px; // 确保 placeholder 垂直居中
    }

    // 选中后显示的文本（截断处理）
    :deep(.ant-select-selection-item) {
      font-size: 13px;
      font-weight: 500;
      line-height: 30px; // 与高度接近，确保垂直居中
      color: #fff;
      padding-right: 10px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      position: static !important; // 覆盖可能的绝对定位
      transform: none !important; // 覆盖可能的 transform
      margin: 0 !important; // 清除 margin

      // 修复选中后内容宽度溢出问题
      .select-option-content {
        min-width: 0;
      }
    }

    // 下拉箭头图标
    :deep(.ant-select-arrow) {
      color: rgba(96, 165, 250, 0.6);
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
      font-size: 13px;
      color: #e2e8f0;
      font-weight: 500;
      font-family: 'JetBrains Mono', monospace;
      word-break: break-word;
    }
  }

  // 紧凑模式下的特殊样式
  &.compact {
    .model-select {
      max-width: 180px;
      min-width: 120px;
    }
  }
}
</style>

<style lang="less">
// 全局样式：下拉选项样式优化
.model-select-dropdown.tech-dropdown {
  background-color: #0f172a !important;
  border: 1px solid rgba(59, 130, 246, 0.2);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
  padding: 4px;

  .ant-select-item {
    padding: 8px 12px;
    font-size: 13px;
    line-height: 1.5;
    color: #94a3b8;
    border-radius: 4px;
    margin-bottom: 2px;
    font-family: 'JetBrains Mono', monospace;

    &:hover {
      background: rgba(59, 130, 246, 0.1);
      color: #e2e8f0;
    }

    &.ant-select-item-option-selected {
      background: rgba(59, 130, 246, 0.15);
      color: #60A5FA;
      font-weight: 600;
      
      .option-name {
        color: #60A5FA;
      }
    }
  }
}
</style>

