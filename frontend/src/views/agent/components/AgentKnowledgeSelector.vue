<template>
  <div class="agent-knowledge-selector">
    <div class="selector-label">
      <Icon icon="ant-design:book-outlined" class="label-icon" />
      <span>{{ t('agent.knowledgeSelector.label') }}</span>
      <a-tooltip :title="t('agent.knowledgeSelector.tooltip')">
        <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
      </a-tooltip>
    </div>
    <a-select
      v-model:value="selectedKnowledgeIds"
      mode="multiple"
      :loading="loading"
      :placeholder="placeholder || t('agent.knowledgeSelector.placeholder')"
      :max-tag-count="2"
      style="width: 100%"
      class="tech-select"
      :dropdown-class-name="'tech-dropdown'"
      @change="handleChange"
    >
      <a-select-option
        v-for="kb in knowledgeList"
        :key="kb.id"
        :value="kb.id"
      >
        <div class="knowledge-option">
          <span class="knowledge-icon">📚</span>
          <div class="knowledge-info">
            <div class="knowledge-name">{{ kb.name }}</div>
            <div class="knowledge-desc">
              {{ kb.description || t('common.noDesc') }}
              <span v-if="kb.documentCount" class="doc-count">
                ({{ kb.documentCount }} {{ t('agent.knowledgeSelector.docCount') }})
              </span>
            </div>
          </div>
        </div>
      </a-select-option>
    </a-select>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { Icon } from '@/components/Icon';
import { message } from 'ant-design-vue';
import { getKnowledgeList } from '../agent.api';
import type { KnowledgeInfo } from '../agent.types';

const { t } = useI18n();

const props = withDefaults(
  defineProps<{
    modelValue?: string[];
    placeholder?: string;
  }>(),
  {
  }
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void;
  (e: 'change', value: string[], knowledgeList: KnowledgeInfo[]): void;
}>();

const selectedKnowledgeIds = ref<string[]>(props.modelValue || []);
const knowledgeList = ref<KnowledgeInfo[]>([]);
const loading = ref(false);

// 计算当前选中的知识库列表
const selectedKnowledgeList = computed(() => {
  return knowledgeList.value.filter((kb) =>
    selectedKnowledgeIds.value.includes(kb.id)
  );
});

// 监听外部值变化
watch(
  () => props.modelValue,
  (newValue) => {
    selectedKnowledgeIds.value = newValue || [];
  }
);

// 加载知识库列表
const loadKnowledgeList = async () => {
  loading.value = true;
  try {
    const result = await getKnowledgeList();
    knowledgeList.value = result;
  } catch (error) {
    message.error(t('agent.knowledgeSelector.loadingError'));
    console.error('加载知识库列表失败:', error);
  } finally {
    loading.value = false;
  }
};

// 处理选择变化
const handleChange = (value: string[]) => {
  selectedKnowledgeIds.value = value;
  emit('update:modelValue', value);
  
  // 使用 computed 属性获取选中的知识库列表
  emit('change', value, selectedKnowledgeList.value);
};

onMounted(() => {
  loadKnowledgeList();
});

// 暴露方法和属性
defineExpose({
  loadKnowledgeList,
  selectedKnowledgeList, // 暴露选中的知识库列表（computed 属性）
});
</script>

<style scoped lang="less">
.agent-knowledge-selector {
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

    .help-icon {
      color: rgba(148, 163, 184, 0.6);
      font-size: 12px;
      cursor: help;
      transition: color 0.2s;

      &:hover {
        color: #60A5FA;
      }
    }
  }

  :deep(.ant-select) {
    font-family: 'JetBrains Mono', monospace;
    
    .ant-select-selector {
      background-color: rgba(0, 0, 0, 0.2) !important;
      border: 1px solid rgba(59, 130, 246, 0.2) !important;
      border-radius: 4px;
      color: #fff !important;
      transition: all 0.3s;
    }
    
    &:hover .ant-select-selector, &.ant-select-focused .ant-select-selector {
      border-color: #60A5FA !important;
      box-shadow: 0 0 8px rgba(59, 130, 246, 0.2);
    }
    
    .ant-select-selection-placeholder {
      color: rgba(148, 163, 184, 0.4);
    }
    
    .ant-select-arrow {
      color: rgba(96, 165, 250, 0.6);
    }
    
    .ant-select-selection-item {
      background: rgba(59, 130, 246, 0.15);
      border: 1px solid rgba(59, 130, 246, 0.3);
      border-radius: 4px;
      color: #e2e8f0;
      
      .ant-select-selection-item-remove {
        color: rgba(148, 163, 184, 0.8);
        &:hover {
          color: #fff;
        }
      }
    }
    
    .ant-select-clear {
      background: #0f172a;
      color: rgba(148, 163, 184, 0.8);
      &:hover {
        color: #fff;
      }
    }
  }
}

.knowledge-option {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 4px 0;

  .knowledge-icon {
    font-size: 16px;
    flex-shrink: 0;
    margin-top: 2px;
  }

  .knowledge-info {
    flex: 1;
    min-width: 0;

    .knowledge-name {
      font-weight: 500;
      color: #e2e8f0;
      margin-bottom: 2px;
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
    }

    .knowledge-desc {
      font-size: 12px;
      color: #94a3b8;
      line-height: 1.4;

      .doc-count {
        color: #60A5FA;
        margin-left: 4px;
      }
    }
  }
}
</style>

<style lang="less">
.tech-dropdown {
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