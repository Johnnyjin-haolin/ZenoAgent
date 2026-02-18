<template>
  <a-modal
    v-model:open="visible"
    :title="isEdit ? t('knowledgeBase.form.editTitle') : t('knowledgeBase.form.createTitle')"
    :width="600"
    :confirm-loading="loading"
    class="tech-modal"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      layout="vertical"
      class="tech-form"
    >
      <a-form-item :label="t('knowledgeBase.form.name')" name="name">
        <a-input
          v-model:value="formData.name"
          :placeholder="t('knowledgeBase.form.namePlaceholder')"
          :maxlength="100"
          show-count
          class="tech-input"
        />
      </a-form-item>

      <a-form-item :label="t('knowledgeBase.form.desc')" name="description">
        <a-textarea
          v-model:value="formData.description"
          :placeholder="t('knowledgeBase.form.descPlaceholder')"
          :rows="4"
          :maxlength="500"
          show-count
          class="tech-textarea"
        />
      </a-form-item>

      <a-form-item :label="t('knowledgeBase.form.embeddingModel')" name="embeddingModelId" :required="!isEdit">
        <a-select
          v-model:value="formData.embeddingModelId"
          :disabled="isEdit"
          :loading="loadingModels"
          :placeholder="t('knowledgeBase.form.embeddingModelPlaceholder')"
          :not-found-content="loadingModels ? undefined : t('knowledgeBase.form.noModel')"
          class="tech-select"
          :dropdown-class-name="'tech-select-dropdown'"
        >
          <a-select-option
            v-for="model in embeddingModels"
            :key="model.id"
            :value="model.id"
          >
            <div class="model-option">
              <span class="model-name">{{ model.displayName || model.name || model.id }}</span>
              <span v-if="model.description" class="model-desc">
                - {{ model.description }}
              </span>
            </div>
          </a-select-option>
        </a-select>
        <div v-if="isEdit" class="form-hint">
          <Icon icon="ant-design:info-circle-outlined" />
          {{ t('knowledgeBase.form.embeddingModelHint') }}
        </div>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, nextTick, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import { Icon } from '@/components/Icon';
import type { FormInstance, Rule } from 'ant-design-vue/es/form';
import {
  createKnowledgeBase,
  updateKnowledgeBase,
} from '@/api/knowledge-base.api';
import { getEmbeddingModels } from '@/views/agent/agent.api';
import type { ModelInfo } from '@/views/agent/agent.types';
import type {
  KnowledgeBase,
  CreateKnowledgeBaseRequest,
  UpdateKnowledgeBaseRequest,
} from '@/types/knowledge-base.types';

const props = withDefaults(
  defineProps<{
    open?: boolean;
    knowledgeBase?: KnowledgeBase | null;
  }>(),
  {
    open: false,
    knowledgeBase: null,
  }
);

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'success'): void;
}>();

const { t } = useI18n();
const formRef = ref<FormInstance>();
const loading = ref(false);

// 向量模型列表（从后端动态获取）
const embeddingModels = ref<ModelInfo[]>([]);
const loadingModels = ref(false);

// 表单数据
const formData = reactive<CreateKnowledgeBaseRequest & { embeddingModelId?: string }>({
  name: '',
  description: '',
  embeddingModelId: '',
});

// 是否编辑模式
const isEdit = computed(() => !!props.knowledgeBase);

// 可见性（双向绑定）
const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
});

// 表单验证规则
const rules = computed<Record<string, Rule[]>>(() => ({
  name: [
    { required: true, message: t('knowledgeBase.form.namePlaceholder'), trigger: 'blur' },
    { min: 1, max: 100, message: t('knowledgeBase.form.nameError'), trigger: 'blur' },
  ],
  embeddingModelId: [
    { required: true, message: t('knowledgeBase.form.embeddingModelPlaceholder'), trigger: 'change' },
  ],
}));

// 加载 Embedding 模型列表
const loadEmbeddingModels = async () => {
  if (embeddingModels.value.length > 0) {
    // 已经加载过，不再重复加载
    return;
  }

  loadingModels.value = true;
  try {
    const models = await getEmbeddingModels();
    embeddingModels.value = models;

    // 如果有默认模型，设置为默认值（仅在创建模式下）
    if (models.length > 0 && !isEdit.value && !formData.embeddingModelId) {
      const defaultModel = models.find((m) => m.isDefault) || models[0];
      formData.embeddingModelId = defaultModel.id;
    }
  } catch (error) {
    console.error('获取向量模型列表失败:', error);
    message.error(t('knowledgeBase.form.loadModelError'));
    // 降级处理：使用空列表
    embeddingModels.value = [];
  } finally {
    loadingModels.value = false;
  }
};

// 重置表单到默认值
const resetForm = async () => {
  formData.name = '';
  formData.description = '';
  
  // 确保模型列表已加载
  await loadEmbeddingModels();
  
  // 如果有模型列表，使用第一个或默认模型
  if (embeddingModels.value.length > 0) {
    const defaultModel = embeddingModels.value.find((m) => m.isDefault) || embeddingModels.value[0];
    formData.embeddingModelId = defaultModel.id;
  } else {
    formData.embeddingModelId = '';
  }
  
  // 清除验证状态
  nextTick(() => {
    formRef.value?.resetFields();
    formRef.value?.clearValidate();
  });
};

// 填充表单数据（编辑模式）
const fillForm = (kb: KnowledgeBase) => {
  if (!kb) {
    return;
  }
  // 直接设置表单数据
  formData.name = kb.name || '';
  formData.description = kb.description || '';
  formData.embeddingModelId = kb.embeddingModelId || '';
  // 清除验证状态，但不重置字段值
  nextTick(() => {
    formRef.value?.clearValidate();
  });
};

// 组件挂载时加载模型列表
onMounted(() => {
  loadEmbeddingModels();
});

// 监听弹窗打开和 knowledgeBase 变化，根据模式填充或重置表单
watch(
  [() => props.open, () => props.knowledgeBase],
  async ([open, kb], [oldOpen, oldKb]) => {
    if (open) {
      // 确保模型列表已加载
      await loadEmbeddingModels();
      
      // 弹窗打开时，使用 nextTick 确保 DOM 和 props 都已更新
      await nextTick();
      const currentKb = props.knowledgeBase;
      if (currentKb && currentKb.id) {
        // 编辑模式：填充表单数据
        fillForm(currentKb);
      } else {
        // 创建模式：重置表单
        await resetForm();
      }
    } else if (oldOpen && !open) {
      // 弹窗从打开变为关闭时，重置表单（确保下次打开时数据正确）
      await resetForm();
    }
  },
  { immediate: false }
);

// 提交表单
const handleSubmit = async () => {
  try {
    await formRef.value?.validate();
    loading.value = true;

    if (isEdit.value && props.knowledgeBase) {
      // 编辑模式
      const updateData: UpdateKnowledgeBaseRequest = {
        name: formData.name,
        description: formData.description || undefined,
      };
      await updateKnowledgeBase(props.knowledgeBase.id, updateData);
      message.success(t('common.updateSuccess'));
    } else {
      // 创建模式
      const createData: CreateKnowledgeBaseRequest = {
        name: formData.name,
        description: formData.description || undefined,
        embeddingModelId: formData.embeddingModelId!,
      };
      await createKnowledgeBase(createData);
      message.success(t('common.createSuccess'));
    }

    emit('success');
    visible.value = false;
  } catch (error: any) {
    if (error?.errorFields) {
      // 表单验证错误
      return;
    }
    message.error(`${t('common.operationFailed')}: ` + (error?.message || 'Unknown error'));
  } finally {
    loading.value = false;
  }
};

// 取消
const handleCancel = () => {
  visible.value = false;
  // 重置表单会在 watch 中处理
};
</script>

<style lang="less">
/* Global styles for Tech Modal */
.tech-modal {
  .ant-modal-content {
    background-color: rgba(15, 23, 42, 0.95) !important;
    backdrop-filter: blur(20px);
    border: 1px solid rgba(59, 130, 246, 0.2);
    box-shadow: 0 0 30px rgba(0, 0, 0, 0.5);
    border-radius: 12px;
  }
  
  .ant-modal-header {
    background: transparent;
    border-bottom: 1px solid rgba(59, 130, 246, 0.2);
    
    .ant-modal-title {
      color: #60A5FA;
      font-family: 'JetBrains Mono', monospace;
      font-weight: 600;
      letter-spacing: 1px;
    }
  }
  
  .ant-modal-close {
    color: rgba(148, 163, 184, 0.8);
    
    &:hover {
      color: #fff;
    }
  }
  
  .ant-modal-body {
    padding: 24px;
  }
  
  .ant-modal-footer {
    background: transparent;
    border-top: 1px solid rgba(59, 130, 246, 0.2);
    padding: 16px 24px;
    
    .ant-btn-default {
      background: transparent;
      border: 1px solid rgba(148, 163, 184, 0.3);
      color: #94a3b8;
      
      &:hover {
        border-color: #60A5FA;
        color: #60A5FA;
      }
    }
    
    .ant-btn-primary {
      background: rgba(59, 130, 246, 0.2);
      border: 1px solid rgba(59, 130, 246, 0.5);
      color: #60A5FA;
      text-shadow: 0 0 10px rgba(59, 130, 246, 0.5);
      
      &:hover {
        background: rgba(59, 130, 246, 0.3);
        border-color: #60A5FA;
        color: #fff;
      }
    }
  }
}

.tech-select-dropdown {
  background-color: #0f172a !important;
  border: 1px solid rgba(59, 130, 246, 0.2);
  
  .ant-select-item {
    color: #e2e8f0;
    font-family: 'JetBrains Mono', monospace;
    font-size: 12px;
    
    &-option-active {
      background-color: rgba(59, 130, 246, 0.1) !important;
    }
    
    &-option-selected {
      background-color: rgba(59, 130, 246, 0.2) !important;
      color: #60A5FA;
    }
  }
}
</style>

<style scoped lang="less">
.tech-form {
  :deep(.ant-form-item-label > label) {
    color: #e2e8f0;
    font-family: 'JetBrains Mono', monospace;
    font-size: 13px;
  }
  
  .tech-input, .tech-textarea {
    background: rgba(0, 0, 0, 0.2) !important;
    border: 1px solid rgba(59, 130, 246, 0.2) !important;
    border-radius: 4px;
    color: #fff !important;
    font-family: 'Inter', sans-serif;
    
    &:hover, &:focus {
      border-color: #60A5FA !important;
      box-shadow: 0 0 8px rgba(59, 130, 246, 0.2);
    }
    
    &::placeholder {
      color: rgba(148, 163, 184, 0.4);
    }

    /* Fix for show-count wrapper */
    :deep(.ant-input), :deep(textarea) {
      background-color: transparent !important;
      color: #fff !important;
      border: none;
    }
  }
  
  :deep(.ant-input-show-count-suffix) {
    color: rgba(148, 163, 184, 0.5);
  }
  
  .tech-select {
    :deep(.ant-select-selector) {
      background-color: rgba(0, 0, 0, 0.2) !important;
      border-color: rgba(59, 130, 246, 0.2) !important;
      color: #fff !important;
      font-family: 'Inter', sans-serif;
    }
    
    :deep(.ant-select-arrow) {
      color: rgba(96, 165, 250, 0.6);
    }
  }
}

.model-option {
  display: flex;
  align-items: center;
  gap: 8px;

  .model-name {
    font-weight: 500;
    color: #e2e8f0;
  }

  .model-desc {
    color: rgba(148, 163, 184, 0.6);
    font-size: 12px;
  }
}

.form-hint {
  color: #94a3b8;
  font-size: 12px;
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(59, 130, 246, 0.05);
  border-radius: 4px;
  border: 1px solid rgba(59, 130, 246, 0.1);
  
  .anticon {
    color: #60A5FA;
  }
}
</style>

