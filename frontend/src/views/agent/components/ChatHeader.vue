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
        {{ t('agent.config') }}
      </a-button>
      
      <a-dropdown v-if="brandLinks && brandLinks.length > 0" placement="bottomRight" overlayClassName="header-help-dropdown">
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
import { useI18n } from 'vue-i18n';

const { t } = useI18n();

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
  padding: 0 24px;
  background: rgba(2, 4, 8, 0.5);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(59, 130, 246, 0.15);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  height: 64px;
  position: relative;
  z-index: 10;

  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    height: 1px;
    background: linear-gradient(
      90deg,
      transparent,
      rgba(59, 130, 246, 0.5),
      transparent
    );
    opacity: 0.5;
  }

  .header-title {
    flex: 1;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    
    .action-btn {
      color: rgba(255, 255, 255, 0.6);
      transition: all 0.3s ease;
      
      &:hover {
        background-color: rgba(59, 130, 246, 0.1);
        color: #60A5FA;
        text-shadow: 0 0 8px rgba(59, 130, 246, 0.5);
      }
    }
  }
}
</style>

<style lang="less">
.header-help-dropdown {
  .ant-dropdown-menu {
    background: rgba(2, 4, 8, 0.9) !important;
    backdrop-filter: blur(12px);
    border: 1px solid rgba(59, 130, 246, 0.2);
    box-shadow: 0 0 20px rgba(59, 130, 246, 0.15);
    padding: 4px;
    border-radius: 8px;
  }

  .ant-dropdown-menu-item {
    color: #cbd5e1 !important; // 浅灰色，确保在深色背景下可见
    font-family: 'JetBrains Mono', monospace;
    border-radius: 4px;
    transition: all 0.2s;
    margin-bottom: 2px;
    padding: 8px 16px;

    &:hover,
    &-active {
      background-color: rgba(59, 130, 246, 0.15) !important;
      color: #60A5FA !important;
    }

    a {
      color: inherit !important;
      text-decoration: none;
      
      &:hover {
        color: inherit !important;
      }
    }
  }
}
</style>


