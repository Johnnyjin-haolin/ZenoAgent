<template>
  <a-modal
    v-model:open="visible"
    :title="t('knowledgeBase.text.title')"
    :width="800"
    :confirm-loading="loading"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 4 }"
      :wrapper-col="{ span: 20 }"
    >
      <a-form-item :label="t('knowledgeBase.text.docTitle')" name="title">
        <a-input
          v-model:value="formData.title"
          :placeholder="t('knowledgeBase.text.titlePlaceholder')"
          :maxlength="200"
          show-count
        />
      </a-form-item>

      <a-form-item :label="t('knowledgeBase.text.content')" name="content">
        <a-textarea
          v-model:value="formData.content"
          :placeholder="t('knowledgeBase.text.contentPlaceholder')"
          :rows="15"
          :maxlength="100000"
          show-count
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import type { FormInstance, Rule } from 'ant-design-vue/es/form';
import { createTextDocument } from '@/api/knowledge-base.api';
import type { CreateTextDocumentRequest } from '@/types/knowledge-base.types';

const props = defineProps<{
  open?: boolean;
  knowledgeBaseId: string;
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'success'): void;
}>();

const { t } = useI18n();
const formRef = ref<FormInstance>();
const loading = ref(false);

// 表单数据
const formData = reactive<CreateTextDocumentRequest>({
  title: '',
  content: '',
});

// 可见性（双向绑定）
const visible = computed({
  get: () => props.open || false,
  set: (value) => emit('update:open', value),
});

// 表单验证规则
const rules = computed<Record<string, Rule[]>>(() => ({
  title: [
    { required: true, message: t('knowledgeBase.text.titleError'), trigger: 'blur' },
    { min: 1, max: 200, message: t('knowledgeBase.text.titleLengthError'), trigger: 'blur' },
  ],
  content: [
    { required: true, message: t('knowledgeBase.text.contentError'), trigger: 'blur' },
    { min: 1, max: 100000, message: t('knowledgeBase.text.contentLengthError'), trigger: 'blur' },
  ],
}));

// 提交表单
const handleSubmit = async () => {
  try {
    await formRef.value?.validate();
    loading.value = true;

    await createTextDocument(props.knowledgeBaseId, {
      title: formData.title,
      content: formData.content,
    });

    message.success(t('knowledgeBase.text.success'));
    emit('success');
    visible.value = false;
  } catch (error: any) {
    if (error?.errorFields) {
      // 表单验证错误
      return;
    }
    message.error(`${t('knowledgeBase.text.error')}: ` + (error?.message || 'Unknown error'));
  } finally {
    loading.value = false;
  }
};

// 取消
const handleCancel = () => {
  visible.value = false;
  formRef.value?.resetFields();
  formData.title = '';
  formData.content = '';
};

// 监听弹窗打开，重置表单
watch(visible, (open) => {
  if (!open) {
    formRef.value?.resetFields();
    formData.title = '';
    formData.content = '';
  }
});
</script>

<style scoped lang="less">
// 样式可以根据需要添加
</style>

