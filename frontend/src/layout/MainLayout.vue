<template>
  <a-layout class="main-layout">
    <!-- Global Tech Background -->
    <TechBackground class="layout-bg" />

    <!-- Sidebar -->
    <a-layout-sider
      v-model:collapsed="collapsed"
      :trigger="null"
      collapsible
      class="glass-sidebar"
      theme="dark"
      :width="260"
      :collapsedWidth="72"
    >
      <div class="logo-container" :class="{ 'collapsed': collapsed }" @click="goHome">
        <div class="logo-icon">
          <span class="logo-emoji">✨</span>
        </div>
        <span class="logo-text" v-show="!collapsed">Zeno Agent</span>
      </div>
      
      <a-menu
        v-model:selectedKeys="selectedKeys"
        mode="inline"
        class="glass-menu"
      >
        <a-menu-item key="agent">
          <template #icon>
            <message-outlined />
          </template>
          <router-link to="/agent">
            <span>{{ t('menu.agent') }}</span>
          </router-link>
        </a-menu-item>
        
        <a-menu-item key="knowledge-bases">
          <template #icon>
            <book-outlined />
          </template>
          <router-link to="/knowledge-bases">
            <span>{{ t('menu.knowledgeBase') }}</span>
          </router-link>
        </a-menu-item>
      </a-menu>
      
      <div class="sidebar-footer" @click="toggleCollapse">
        <menu-fold-outlined v-if="!collapsed" />
        <menu-unfold-outlined v-else />
      </div>
    </a-layout-sider>

    <!-- Main Content -->
    <a-layout class="content-layout">
      <!-- Top Header -->
      <a-layout-header class="glass-header">
        <div class="header-left">
          <h2 class="page-title">{{ t('layout.title') }}</h2>
        </div>
        <div class="header-right">
          <a-tooltip :title="currentLocale === 'zh-CN' ? 'Switch to English' : '切换为中文'">
            <a-button type="text" class="lang-btn" @click="toggleLanguage">
              <template #icon>
                <translation-outlined />
              </template>
            </a-button>
          </a-tooltip>
          
          <div class="user-profile-simple">
            <a-avatar 
              :size="36" 
              class="user-avatar-simple"
            >
              <template #icon><UserOutlined /></template>
            </a-avatar>
          </div>
        </div>
      </a-layout-header>

      <!-- Content -->
      <a-layout-content class="glass-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import {
  MessageOutlined,
  BookOutlined,
  SettingOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  TranslationOutlined,
  UserOutlined
} from '@ant-design/icons-vue';
import TechBackground from '@/components/TechBackground.vue';
import TechBorder from '@/components/TechBorder.vue';

const { t, locale } = useI18n();
const collapsed = ref(false);
const router = useRouter();
const route = useRoute();
const selectedKeys = ref<string[]>([]);

// Go Home
const goHome = () => {
  router.push('/');
};

// Sync menu selection with route
watch(
  () => route.path,
  (path) => {
    if (path.startsWith('/agent')) {
      selectedKeys.value = ['agent'];
    } else if (path.startsWith('/knowledge-bases')) {
      selectedKeys.value = ['knowledge-bases'];
    }
  },
  { immediate: true }
);

const currentRouteTitle = computed(() => {
  return route.meta.title || 'Zeno Agent';
});

const currentLocale = computed(() => locale.value);

const toggleCollapse = () => {
  collapsed.value = !collapsed.value;
};

const toggleLanguage = () => {
  const newLocale = locale.value === 'en-US' ? 'zh-CN' : 'en-US';
  locale.value = newLocale;
  localStorage.setItem('locale', newLocale);
};
</script>

<style lang="less" scoped>
.main-layout {
  min-height: 100vh;
  background: transparent;
  position: relative;
  overflow: hidden;
}

.layout-bg {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 0;
}

/* Glass Sidebar */
.glass-sidebar {
  background: rgba(15, 23, 42, 0.6); /* Cyber Surface */
  border-right: 1px solid rgba(59, 130, 246, 0.1);
  backdrop-filter: blur(12px);
  z-index: 10;
  
  :deep(.ant-layout-sider-children) {
    display: flex;
    flex-direction: column;
  }
}

.logo-container {
  height: 72px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  overflow: hidden;
  white-space: nowrap;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  cursor: pointer;
  
  &:hover {
    background: rgba(255, 255, 255, 0.05);
  }
  
  &.collapsed {
    padding: 0;
    justify-content: center;
  }
  
  .logo-icon {
    width: 32px;
    height: 32px;
    margin-right: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
  }
  
  .logo-text {
    font-family: 'Inter', sans-serif;
    font-size: 18px;
    font-weight: 700;
    background: linear-gradient(90deg, #F8FAFC, #94A3B8);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    letter-spacing: -0.5px;
  }
}

/* Glass Menu */
.glass-menu {
  flex: 1;
  border-right: none;
  background: transparent;
  padding-top: 16px;
  
  .menu-spacer {
    flex: 1;
  }
  
  :deep(.ant-menu-item) {
    height: 44px;
    line-height: 44px;
    margin: 4px 12px;
    width: auto;
    border-radius: 8px;
    color: var(--color-text-secondary);
    transition: all 0.2s;
    
    &:hover {
      color: var(--color-text-primary);
      background: rgba(255, 255, 255, 0.05);
    }
    
    &.ant-menu-item-selected {
      background: rgba(59, 130, 246, 0.15);
      color: var(--google-blue);
      font-weight: 500;
      box-shadow: inset 0 0 0 1px rgba(59, 130, 246, 0.2);
      
      .anticon {
        color: var(--google-blue);
      }
    }
    
    .anticon {
      font-size: 18px;
      transition: color 0.2s;
    }
  }
}

.sidebar-footer {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--color-text-disabled);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  transition: all 0.2s;
  
  &:hover {
    color: var(--color-text-primary);
    background: rgba(255, 255, 255, 0.05);
  }
}

/* Content Layout */
.content-layout {
  background: transparent;
  z-index: 1;
}

.glass-header {
  background: rgba(5, 11, 20, 0.6);
  backdrop-filter: blur(12px);
  height: 72px;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(59, 130, 246, 0.1);
  
  .page-title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: var(--color-text-primary);
    letter-spacing: -0.5px;
  }
  
  .header-right {
    display: flex;
    align-items: center;
    height: 100%;
  }
  
  .lang-btn {
    color: var(--color-text-secondary);
    margin-right: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    
    &:hover {
      color: var(--color-text-primary);
      background: rgba(255, 255, 255, 0.1);
    }
  }
  
  .user-profile-simple {
    display: flex;
    align-items: center;
    justify-content: center;
    
    .user-avatar-simple {
      background: rgba(59, 130, 246, 0.1);
      color: #60A5FA;
      border: 1px solid rgba(59, 130, 246, 0.2);
      transition: all 0.3s ease;
      cursor: default;
      
      &:hover {
        background: rgba(59, 130, 246, 0.2);
        border-color: rgba(59, 130, 246, 0.4);
        box-shadow: 0 0 12px rgba(59, 130, 246, 0.3);
        color: #93C5FD;
      }
    }
  }
}

.glass-content {
  margin: 0;
  padding: 0;
  background: transparent;
  overflow: hidden;
  position: relative;
  height: calc(100vh - 72px);
  display: flex;
  flex-direction: column;
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
