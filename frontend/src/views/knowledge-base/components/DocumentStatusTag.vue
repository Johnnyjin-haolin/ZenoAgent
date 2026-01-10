<template>
  <a-tag :color="statusColor" :icon="statusIcon">
    {{ statusText }}
  </a-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Icon } from '@/components/Icon';
import type { Document } from '@/types/knowledge-base.types';

const props = defineProps<{
  status: Document['status'];
}>();

// 状态配置
const statusConfig = computed(() => {
  const configs: Record<
    Document['status'],
    { color: string; icon: string; text: string }
  > = {
    DRAFT: {
      color: 'default',
      icon: 'ant-design:file-outlined',
      text: '草稿',
    },
    BUILDING: {
      color: 'processing',
      icon: 'ant-design:loading-outlined',
      text: '处理中',
    },
    COMPLETE: {
      color: 'success',
      icon: 'ant-design:check-circle-outlined',
      text: '完成',
    },
    FAILED: {
      color: 'error',
      icon: 'ant-design:close-circle-outlined',
      text: '失败',
    },
  };
  return configs[props.status] || configs.DRAFT;
});

const statusColor = computed(() => statusConfig.value.color);
const statusIcon = computed(() => statusConfig.value.icon);
const statusText = computed(() => statusConfig.value.text);
</script>

