<template>
  <a-modal
    v-model:open="visible"
    title="上传文档"
    :width="600"
    :confirm-loading="uploading"
    @ok="handleUpload"
    @cancel="handleCancel"
  >
    <a-tabs v-model:activeKey="uploadType">
      <!-- 单文件上传 -->
      <a-tab-pane key="single" tab="单文件上传">
        <a-upload-dragger
          v-model:fileList="fileList"
          :before-upload="beforeUpload"
          :accept="acceptedTypes"
          :max-count="1"
          @remove="handleRemove"
        >
          <p class="ant-upload-drag-icon">
            <Icon icon="ant-design:inbox-outlined" />
          </p>
          <p class="ant-upload-text">点击或拖拽文件到此区域上传</p>
          <p class="ant-upload-hint">
            支持：txt, md, pdf, docx, doc, xlsx, xls, pptx, ppt（最大150MB）
          </p>
        </a-upload-dragger>
      </a-tab-pane>

      <!-- ZIP批量导入 -->
      <a-tab-pane key="zip" tab="ZIP批量导入">
        <a-upload-dragger
          v-model:fileList="zipFileList"
          :before-upload="beforeZipUpload"
          accept=".zip"
          :max-count="1"
          @remove="handleRemoveZip"
        >
          <p class="ant-upload-drag-icon">
            <Icon icon="ant-design:file-zip-outlined" />
          </p>
          <p class="ant-upload-text">上传ZIP压缩包</p>
          <p class="ant-upload-hint">
            将自动解压并导入所有支持的文件
          </p>
        </a-upload-dragger>
      </a-tab-pane>
    </a-tabs>

    <!-- 上传进度（如果支持） -->
    <div v-if="uploadProgress > 0 && uploadProgress < 100" class="upload-progress">
      <a-progress :percent="uploadProgress" />
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { message } from 'ant-design-vue';
import type { UploadFile } from 'ant-design-vue';
import { Icon } from '@/components/Icon';
import { uploadDocument, importDocumentsFromZip } from '@/api/knowledge-base.api';

const props = defineProps<{
  open?: boolean;
  knowledgeBaseId: string;
}>();

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void;
  (e: 'success'): void;
}>();

const visible = computed({
  get: () => props.open || false,
  set: (value) => emit('update:open', value),
});

const uploadType = ref<'single' | 'zip'>('single');
const fileList = ref<UploadFile[]>([]);
const zipFileList = ref<UploadFile[]>([]);
const uploading = ref(false);
const uploadProgress = ref(0);

// 支持的文件类型
const acceptedTypes = [
  'text/plain',
  'text/markdown',
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'application/vnd.ms-powerpoint',
];

const MAX_FILE_SIZE = 150 * 1024 * 1024; // 150MB

// 单文件上传前验证
const beforeUpload = (file: File) => {
  // 验证文件类型
  const isValidType =
    acceptedTypes.includes(file.type) ||
    /\.(txt|md|pdf|docx|doc|xlsx|xls|pptx|ppt)$/i.test(file.name);

  if (!isValidType) {
    message.error('不支持的文件类型');
    return false;
  }

  // 验证文件大小
  if (file.size > MAX_FILE_SIZE) {
    message.error('文件大小不能超过150MB');
    return false;
  }

  // 阻止自动上传
  return false;
};

// ZIP文件上传前验证
const beforeZipUpload = (file: File) => {
  if (!file.name.toLowerCase().endsWith('.zip')) {
    message.error('请上传ZIP格式的压缩包');
    return false;
  }

  if (file.size > MAX_FILE_SIZE * 10) {
    // ZIP文件可以稍大一些
    message.error('ZIP文件大小不能超过1.5GB');
    return false;
  }

  // 阻止自动上传
  return false;
};

// 移除文件
const handleRemove = () => {
  fileList.value = [];
};

// 移除ZIP文件
const handleRemoveZip = () => {
  zipFileList.value = [];
};

// 上传
const handleUpload = async () => {
  if (uploadType.value === 'single') {
    // 单文件上传
    if (fileList.value.length === 0) {
      message.warning('请选择要上传的文件');
      return;
    }

    const file = fileList.value[0].originFileObj;
    if (!file) {
      message.error('文件不存在');
      return;
    }

    uploading.value = true;
    uploadProgress.value = 0;

    try {
      await uploadDocument(props.knowledgeBaseId, file);
      message.success('文件上传成功，正在处理中');
      emit('success');
      visible.value = false;
      fileList.value = [];
    } catch (error: any) {
      message.error('上传失败: ' + (error?.message || '未知错误'));
    } finally {
      uploading.value = false;
      uploadProgress.value = 0;
    }
  } else {
    // ZIP批量导入
    if (zipFileList.value.length === 0) {
      message.warning('请选择要上传的ZIP文件');
      return;
    }

    const zipFile = zipFileList.value[0].originFileObj;
    if (!zipFile) {
      message.error('文件不存在');
      return;
    }

    uploading.value = true;
    uploadProgress.value = 0;

    try {
      const documents = await importDocumentsFromZip(props.knowledgeBaseId, zipFile);
      message.success(`ZIP导入成功，共导入 ${documents.length} 个文件，正在处理中`);
      emit('success');
      visible.value = false;
      zipFileList.value = [];
    } catch (error: any) {
      message.error('ZIP导入失败: ' + (error?.message || '未知错误'));
    } finally {
      uploading.value = false;
      uploadProgress.value = 0;
    }
  }
};

// 取消
const handleCancel = () => {
  visible.value = false;
  fileList.value = [];
  zipFileList.value = [];
  uploadProgress.value = 0;
};

// 监听弹窗打开，重置状态
watch(visible, (open) => {
  if (!open) {
    fileList.value = [];
    zipFileList.value = [];
    uploadProgress.value = 0;
    uploadType.value = 'single';
  }
});
</script>

<style scoped lang="less">
.upload-progress {
  margin-top: 16px;
}
</style>

