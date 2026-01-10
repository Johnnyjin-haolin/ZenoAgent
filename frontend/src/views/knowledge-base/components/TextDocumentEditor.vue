<template>
  <a-modal
    v-model:open="visible"
    title="创建文本文档"
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
      <a-form-item label="文档标题" name="title">
        <a-input
          v-model:value="formData.title"
          placeholder="请输入文档标题（必填）"
          :maxlength="200"
          show-count
        />
      </a-form-item>

      <a-form-item label="文档内容" name="content">
        <a-textarea
          v-model:value="formData.content"
          placeholder="请输入文档内容（支持Markdown格式）"
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
const rules: Record<string, Rule[]> = {
  title: [
    { required: true, message: '请输入文档标题', trigger: 'blur' },
    { min: 1, max: 200, message: '标题长度在1-200个字符之间', trigger: 'blur' },
  ],
  content: [
    { required: true, message: '请输入文档内容', trigger: 'blur' },
    { min: 1, max: 100000, message: '内容长度在1-100000个字符之间', trigger: 'blur' },
  ],
};

// 提交表单
const handleSubmit = async () => {
  try {
    await formRef.value?.validate();
    loading.value = true;

    await createTextDocument(props.knowledgeBaseId, {
      title: formData.title,
      content: formData.content,
    });

    message.success('文本文档创建成功，正在处理中');
    emit('success');
    visible.value = false;
  } catch (error: any) {
    if (error?.errorFields) {
      // 表单验证错误
      return;
    }
    message.error('创建失败: ' + (error?.message || '未知错误'));
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

