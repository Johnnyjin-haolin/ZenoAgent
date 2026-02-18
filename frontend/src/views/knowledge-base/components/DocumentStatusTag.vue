<template>
  <a-tag :color="statusColor" class="tech-status-tag">
    <template #icon>
      <Icon :icon="statusIcon" :spin="status === 'BUILDING'" />
    </template>
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
      icon: 'ant-design:edit-outlined',
      text: 'DRAFT',
    },
    BUILDING: {
      color: 'processing',
      icon: 'ant-design:sync-outlined',
      text: 'PROCESSING',
    },
    COMPLETE: {
      color: 'success',
      icon: 'ant-design:check-circle-outlined',
      text: 'INDEXED',
    },
    FAILED: {
      color: 'error',
      icon: 'ant-design:close-circle-outlined',
      text: 'FAILED',
    },
  };
  return configs[props.status] || configs.DRAFT;
});

const statusColor = computed(() => statusConfig.value.color);
const statusIcon = computed(() => statusConfig.value.icon);
const statusText = computed(() => statusConfig.value.text);
</script>

<style scoped>
.tech-status-tag {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
</style>

