<template>
  <div class="chat-header">
    <div class="header-title">
      <!-- Title Removed - Handled by Main Layout -->
    </div>

    <div class="header-actions">
      <a-button type="text" @click="emit('open-config')" class="action-btn">
        <template #icon>
          <Icon icon="ant-design:setting-outlined" />
        </template>
        配置
      </a-button>
      
      <a-dropdown v-if="brandLinks && brandLinks.length > 0" placement="bottomRight">
        <a-button type="text" class="action-btn">
          <template #icon>
            <Icon icon="ant-design:question-circle-outlined" />
          </template>
        </a-button>
        <template #overlay>
          <a-menu>
            <a-menu-item v-for="link in brandLinks" :key="link.label">
              <a :href="link.url" target="_blank" rel="noopener noreferrer">{{ link.label }}</a>
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@/components/Icon';

type BrandLink = {
  label: string;
  url: string;
};

type BrandConfig = {
  name: string;
  logo?: string;
  primaryColor?: string;
  links?: BrandLink[];
  version?: string;
  showFooter?: boolean;
  showTitle?: boolean;
  embedMode?: boolean;
};

defineProps<{
  brandConfig: BrandConfig;
  showBrandTitle: boolean;
  brandLinks: BrandLink[];
  brandVersion: string;
}>();

const emit = defineEmits<{
  (e: 'navigate-knowledge'): void;
  (e: 'open-config'): void;
}>();
</script>

<style scoped lang="less">
.chat-header {
  padding: 8px 24px;
  background: transparent;
  border-bottom: 1px solid rgba(0,0,0,0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  height: 48px;

  .header-title {
    flex: 1;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    
    .action-btn {
      color: #5F6368;
      
      &:hover {
        background-color: rgba(0,0,0,0.04);
        color: #202124;
      }
    }
  }
}
</style>


