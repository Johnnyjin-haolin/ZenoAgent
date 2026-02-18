<template>
  <div ref="scrollRef" class="chat-messages">
    <template v-if="messages.length > 0">
      <AgentMessage
        v-for="msg in messages"
        :key="msg.id"
        :message="msg"
        @confirm-tool="emit('confirm-tool')"
        @reject-tool="emit('reject-tool')"
      />
    </template>

    <div v-else class="welcome-container">
      <div class="welcome-content">
        <div class="holographic-core">
          <div class="core-inner"></div>
          <div class="core-outer"></div>
        </div>
        <h3 class="welcome-title">{{ t('home.title') }} <span class="version">{{ t('home.version') }}</span></h3>
        <p class="welcome-subtitle">{{ t('home.subtitle') }}</p>
        
        <div class="capabilities-grid">
          <div v-for="item in capabilityItems" :key="item.title" class="tech-card">
            <div class="card-glow"></div>
            <div class="card-icon">{{ item.icon }}</div>
            <div class="card-title">{{ item.title }}</div>
            <div class="card-desc">{{ item.desc }}</div>
          </div>
        </div>

        <div class="scenarios-section">
          <div class="section-header">
            <span class="header-line"></span>
            <span class="header-text">{{ t('home.initiate') }}</span>
            <span class="header-line"></span>
          </div>
          <div class="scenario-chips">
            <div
              v-for="prompt in scenarioPrompts"
              :key="prompt"
              class="tech-chip"
              @click="emit('apply-prompt', prompt)"
            >
              <span class="chip-bracket">[</span>
              {{ prompt }}
              <span class="chip-bracket">]</span>
            </div>
          </div>
        </div>
        
        <div class="system-status">
          <span class="status-dot"></span>
          {{ t('home.systemReady') }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import AgentMessage from './AgentMessage.vue';
import type { AgentMessage as AgentMessageType } from '../agent.types';

type CapabilityItem = {
  icon: string;
  title: string;
  desc: string;
};

defineProps<{
  messages: AgentMessageType[];
  capabilityItems: CapabilityItem[];
  scenarioPrompts: string[];
}>();

const emit = defineEmits<{
  (e: 'apply-prompt', prompt: string): void;
  (e: 'confirm-tool'): void;
  (e: 'reject-tool'): void;
}>();

const { t } = useI18n();
const scrollRef = ref<HTMLDivElement | null>(null);

const scrollToBottom = async () => {
  await nextTick();
  if (scrollRef.value) {
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight;
  }
};

defineExpose({ scrollToBottom });
</script>

<style scoped lang="less">
.chat-messages {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px 24px;
  background: transparent;
  min-height: 0;
  scroll-behavior: smooth;

  /* Custom Scrollbar */
  &::-webkit-scrollbar {
    width: 6px;
  }
  
  &::-webkit-scrollbar-track {
    background: rgba(255, 255, 255, 0.02);
  }
  
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 3px;
    
    &:hover {
      background: rgba(255, 255, 255, 0.2);
    }
  }
}

.welcome-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100%;
  padding: 40px 0;
}

.welcome-content {
  max-width: 700px;
  width: 100%;
  text-align: center;
  position: relative;
  z-index: 1;
}

/* Holographic Core Effect */
.holographic-core {
  width: 60px;
  height: 60px;
  margin: 0 auto 24px;
  position: relative;
  
  .core-inner {
    position: absolute;
    inset: 10px;
    background: #3B82F6;
    border-radius: 50%;
    filter: blur(8px);
    opacity: 0.8;
    animation: pulse 2s infinite;
  }
  
  .core-outer {
    position: absolute;
    inset: 0;
    border: 2px solid rgba(59, 130, 246, 0.5);
    border-radius: 50%;
    animation: spin 10s linear infinite;
    
    &::before, &::after {
      content: '';
      position: absolute;
      top: 50%;
      left: 50%;
      width: 120%;
      height: 120%;
      border: 1px dashed rgba(59, 130, 246, 0.3);
      border-radius: 50%;
      transform: translate(-50%, -50%);
    }
  }
}

.welcome-title {
  font-family: 'JetBrains Mono', monospace;
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 8px;
  letter-spacing: -0.5px;
  text-shadow: 0 0 20px rgba(59, 130, 246, 0.5);
  
  .version {
    font-size: 12px;
    color: #60A5FA;
    background: rgba(59, 130, 246, 0.1);
    padding: 2px 6px;
    border-radius: 4px;
    vertical-align: middle;
    border: 1px solid rgba(59, 130, 246, 0.3);
  }
}

.welcome-subtitle {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 32px;
  letter-spacing: 1px;
  text-transform: uppercase;
}

.capabilities-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 40px;
}

.tech-card {
  position: relative;
  background: rgba(2, 4, 8, 0.6);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 12px;
  padding: 16px;
  text-align: left;
  transition: all 0.3s ease;
  overflow: hidden;
  backdrop-filter: blur(10px);
  
  .card-glow {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: radial-gradient(circle at top right, rgba(59, 130, 246, 0.1), transparent 60%);
    opacity: 0;
    transition: opacity 0.3s ease;
  }
  
  &:hover {
    border-color: rgba(59, 130, 246, 0.4);
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
    
    .card-glow {
      opacity: 1;
    }
    
    .card-title {
      color: #60A5FA;
    }
  }
  
  .card-icon {
    font-size: 20px;
    margin-bottom: 12px;
    color: #3B82F6;
  }
  
  .card-title {
    font-family: 'JetBrains Mono', monospace;
    font-size: 14px;
    font-weight: 600;
    color: #e2e8f0;
    margin-bottom: 6px;
    transition: color 0.3s ease;
  }
  
  .card-desc {
    font-size: 12px;
    color: #94a3b8;
    line-height: 1.5;
  }
}

.scenarios-section {
  margin-bottom: 32px;
  
  .section-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    
    .header-line {
      flex: 1;
      height: 1px;
      background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.1), transparent);
    }
    
    .header-text {
      font-family: 'JetBrains Mono', monospace;
      font-size: 10px;
      color: rgba(59, 130, 246, 0.6);
      letter-spacing: 2px;
    }
  }
}

.scenario-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.tech-chip {
  cursor: pointer;
  padding: 6px 16px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  transition: all 0.2s ease;
  
  .chip-bracket {
    color: rgba(59, 130, 246, 0.4);
    transition: color 0.2s ease;
  }
  
  &:hover {
    background: rgba(59, 130, 246, 0.1);
    border-color: rgba(59, 130, 246, 0.3);
    color: #fff;
    
    .chip-bracket {
      color: #60A5FA;
    }
  }
}

.system-status {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  color: rgba(59, 130, 246, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  
  .status-dot {
    width: 6px;
    height: 6px;
    background: #10B981;
    border-radius: 50%;
    box-shadow: 0 0 8px #10B981;
    animation: blink 2s infinite;
  }
}

@keyframes pulse {
  0%, 100% { opacity: 0.8; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.9); }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>

