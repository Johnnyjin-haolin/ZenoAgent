<template>
  <div class="agent-model-selector">
    <div class="selector-label">
      <Icon icon="ant-design:api-outlined" />
      <span>AI 模型</span>
    </div>
    <a-select
      v-model:value="selectedModel"
      :loading="loading"
      :placeholder="placeholder"
      :allow-clear="allowClear"
      style="width: 100%"
      @change="handleChange"
    >
      <!-- 自定义选中后显示的内容（只显示图标+标题） -->
      <template #suffixIcon>
        <Icon icon="ant-design:down-outlined" />
      </template>

      <a-select-option value="">
        <template #label>
          <div class="model-selected">
            <span class="model-icon">🤖</span>
            <span class="model-title">智能选择</span>
          </div>
        </template>
        <div class="model-option">
          <span class="model-icon">🤖</span>
          <div class="model-info">
            <div class="model-name">智能选择（推荐）</div>
            <div class="model-desc">根据任务类型自动选择最优模型</div>
          </div>
        </div>
      </a-select-option>

      <a-select-option
        v-for="model in models"
        :key="model.id"
        :value="model.id"
      >
        <template #label>
          <div class="model-selected">
            <span class="model-icon">{{ model.icon }}</span>
            <span class="model-title">{{ model.displayName }}</span>
          </div>
        </template>
        <div class="model-option">
          <span class="model-icon">{{ model.icon }}</span>
          <div class="model-info">
            <div class="model-name">
              {{ model.displayName }}
              <a-tag v-if="model.isDefault" color="blue" size="small">默认</a-tag>
            </div>
            <div class="model-desc">{{ model.description }}</div>
          </div>
        </div>
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
  }>(),
  {
    placeholder: '选择 AI 模型',
    allowClear: true,
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
    
    // 如果没有选择模型，自动选择默认模型
    if (!selectedModel.value) {
      const defaultModel = models.value.find((m) => m.isDefault);
      if (defaultModel) {
        // 不自动选择，让用户看到"智能选择"选项
        // selectedModel.value = defaultModel.id;
      }
    }
  } catch (error) {
    message.error('加载模型列表失败');
    console.error('加载模型列表失败:', error);
  } finally {
    loading.value = false;
  }
};

// 处理选择变化
const handleChange = (value: string) => {
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
  .selector-label {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 500;
    color: #262626;
  }

  :deep(.ant-select) {
    .ant-select-selector {
      border-radius: 6px;
      min-height: 38px;
    }
    
    // 确保下拉选项有足够的高度
    .ant-select-item {
      padding: 4px 12px;
      min-height: auto;
    }
    
    // 选中项的样式
    .ant-select-item-option-content {
      display: block;
    }
  }
}

// 选中后在选择框中显示的简化样式（只有图标+标题）
.model-selected {
  display: flex;
  align-items: center;
  gap: 8px;
  
  .model-icon {
    font-size: 16px;
    line-height: 1;
    flex-shrink: 0;
  }
  
  .model-title {
    font-size: 14px;
    font-weight: 500;
    color: #262626;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.model-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 6px 4px;
  min-height: 48px;

  .model-icon {
    font-size: 18px;
    line-height: 1;
    flex-shrink: 0;
    margin-top: 2px;
    width: 20px;
    height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .model-info {
    flex: 1;
    min-width: 0;
    overflow: hidden;

    .model-name {
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 500;
      font-size: 14px;
      color: #262626;
      margin-bottom: 4px;
      line-height: 1.4;
    }

    .model-desc {
      font-size: 12px;
      color: #8c8c8c;
      line-height: 1.5;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      word-break: break-word;
      max-width: 100%;
    }
  }
}
</style>

