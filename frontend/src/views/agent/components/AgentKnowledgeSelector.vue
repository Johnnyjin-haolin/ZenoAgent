<template>
  <div class="agent-knowledge-selector">
    <div class="selector-label">
      <Icon icon="ant-design:book-outlined" />
      <span>知识库</span>
      <a-tooltip title="选择要检索的知识库，可多选">
        <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
      </a-tooltip>
    </div>
    <a-select
      v-model:value="selectedKnowledgeIds"
      mode="multiple"
      :loading="loading"
      :placeholder="placeholder"
      :max-tag-count="2"
      style="width: 100%"
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
              {{ kb.description || '暂无描述' }}
              <span v-if="kb.documentCount" class="doc-count">
                ({{ kb.documentCount }} 篇文档)
              </span>
            </div>
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
import { getKnowledgeList } from '../agent.api';
import type { KnowledgeInfo } from '../agent.types';

const props = withDefaults(
  defineProps<{
    modelValue?: string[];
    placeholder?: string;
  }>(),
  {
    placeholder: '选择知识库（可多选）',
  }
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void;
  (e: 'change', value: string[], knowledgeList: KnowledgeInfo[]): void;
}>();

const selectedKnowledgeIds = ref<string[]>(props.modelValue || []);
const knowledgeList = ref<KnowledgeInfo[]>([]);
const loading = ref(false);

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
    message.error('加载知识库列表失败');
    console.error('加载知识库列表失败:', error);
  } finally {
    loading.value = false;
  }
};

// 处理选择变化
const handleChange = (value: string[]) => {
  emit('update:modelValue', value);
  
  const selectedList = knowledgeList.value.filter((kb) =>
    value.includes(kb.id)
  );
  emit('change', value, selectedList);
};

onMounted(() => {
  loadKnowledgeList();
});

// 暴露方法
defineExpose({
  loadKnowledgeList,
});
</script>

<style scoped lang="less">
.agent-knowledge-selector {
  .selector-label {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 500;
    color: #262626;

    .help-icon {
      color: #8c8c8c;
      font-size: 14px;
      cursor: help;
    }
  }

  :deep(.ant-select) {
    .ant-select-selector {
      border-radius: 6px;
    }
  }
}

.knowledge-option {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 4px 0;

  .knowledge-icon {
    font-size: 20px;
    flex-shrink: 0;
    margin-top: 2px;
  }

  .knowledge-info {
    flex: 1;
    min-width: 0;

    .knowledge-name {
      font-weight: 500;
      color: #262626;
      margin-bottom: 2px;
    }

    .knowledge-desc {
      font-size: 12px;
      color: #8c8c8c;
      line-height: 1.4;

      .doc-count {
        color: #1890ff;
      }
    }
  }
}
</style>

