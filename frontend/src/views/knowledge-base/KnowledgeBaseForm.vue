<template>
  <a-modal
    v-model:open="visible"
    :title="isEdit ? '编辑知识库' : '创建知识库'"
    :width="600"
    :confirm-loading="loading"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 18 }"
    >
      <a-form-item label="知识库名称" name="name">
        <a-input
          v-model:value="formData.name"
          placeholder="请输入知识库名称（1-100字符）"
          :maxlength="100"
          show-count
        />
      </a-form-item>

      <a-form-item label="描述" name="description">
        <a-textarea
          v-model:value="formData.description"
          placeholder="请输入描述信息（可选，最多500字符）"
          :rows="4"
          :maxlength="500"
          show-count
        />
      </a-form-item>

      <a-form-item label="向量模型" name="embeddingModelId" :required="!isEdit">
        <a-select
          v-model:value="formData.embeddingModelId"
          :disabled="isEdit"
          :loading="loadingModels"
          placeholder="请选择向量模型"
          :not-found-content="loadingModels ? undefined : '暂无可用向量模型'"
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
          向量模型创建后不可修改
        </div>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, nextTick, onMounted } from 'vue';
import { message } from 'ant-design-vue';
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
const rules: Record<string, Rule[]> = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 1, max: 100, message: '名称长度在1-100个字符之间', trigger: 'blur' },
  ],
  embeddingModelId: [
    { required: true, message: '请选择向量模型', trigger: 'change' },
  ],
};

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
    message.error('获取向量模型列表失败，请刷新重试');
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
    console.warn('fillForm: knowledgeBase is null');
    return;
  }
  console.log('fillForm called with:', kb);
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
    console.log('Watch triggered - open:', open, 'oldOpen:', oldOpen, 'kb:', kb, 'oldKb:', oldKb);
    if (open) {
      // 确保模型列表已加载
      await loadEmbeddingModels();
      
      // 弹窗打开时，使用 nextTick 确保 DOM 和 props 都已更新
      await nextTick();
      const currentKb = props.knowledgeBase;
      console.log('Modal opened, current knowledgeBase:', currentKb);
      if (currentKb && currentKb.id) {
        // 编辑模式：填充表单数据
        console.log('Filling form with knowledgeBase:', currentKb);
        fillForm(currentKb);
      } else {
        // 创建模式：重置表单
        console.log('Resetting form for create mode');
        await resetForm();
      }
    } else if (oldOpen && !open) {
      // 弹窗从打开变为关闭时，重置表单（确保下次打开时数据正确）
      console.log('Modal closed, resetting form');
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
      message.success('更新成功');
    } else {
      // 创建模式
      const createData: CreateKnowledgeBaseRequest = {
        name: formData.name,
        description: formData.description || undefined,
        embeddingModelId: formData.embeddingModelId!,
      };
      await createKnowledgeBase(createData);
      message.success('创建成功');
    }

    emit('success');
    visible.value = false;
  } catch (error: any) {
    if (error?.errorFields) {
      // 表单验证错误
      return;
    }
    message.error('操作失败: ' + (error?.message || '未知错误'));
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

<style scoped lang="less">
.model-option {
  display: flex;
  align-items: center;
  gap: 8px;

  .model-name {
    font-weight: 500;
  }

  .model-desc {
    color: #8c8c8c;
    font-size: 12px;
  }
}

.form-hint {
  color: #8c8c8c;
  font-size: 12px;
  margin-top: 4px;
}
</style>

