<template>
  <div ref="scrollRef" class="chat-messages">
    <div v-if="messages.length > 0" class="messages-inner">
      <AgentMessage
        v-for="msg in messages"
        :key="msg.id"
        :message="msg"
        @confirm-tool="emit('confirm-tool')"
        @reject-tool="emit('reject-tool')"
        @answer-question="(qId, ans) => emit('answer-question', qId, ans)"
      />
    </div>

    <div v-else class="welcome-container">
      <div class="welcome-content">
        <div class="welcome-avatar">
          <div class="avatar-ring"></div>
          <span class="avatar-icon">✦</span>
        </div>

        <h3 class="welcome-greeting">{{ t('agent.greeting') }}</h3>
        <p v-if="agentDescription" class="welcome-agent-desc">{{ agentDescription }}</p>

        <div class="categories-wrap">
          <div v-for="cat in welcomeCategories" :key="cat.label" class="category-col">
            <div class="category-label">{{ cat.label }}</div>
            <div class="category-items">
              <div
                v-for="item in cat.items"
                :key="item"
                class="prompt-chip"
                @click="emit('apply-prompt', item)"
              >
                {{ item }}
              </div>
            </div>
          </div>
        </div>

        <div class="welcome-hint">
          <span class="hint-dot"></span>
          {{ t('agent.greetingHint') }}
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

type WelcomeCategory = {
  label: string;
  items: string[];
};

defineProps<{
  messages: AgentMessageType[];
  welcomeCategories: WelcomeCategory[];
  agentDescription?: string;
}>();

const emit = defineEmits<{
  (e: 'apply-prompt', prompt: string): void;
  (e: 'confirm-tool'): void;
  (e: 'reject-tool'): void;
  (e: 'answer-question', questionId: string, answer: string): void;
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
  // 使用父容器的 CSS 变量，提供自适应水平 padding
  padding: 20px var(--content-padding-x, 24px);
  background: transparent;
  min-height: 0;
  scroll-behavior: smooth;

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

// 消息列表内容列宽限制容器（包裹所有消息条目）
.messages-inner {
  max-width: var(--content-max-width, 900px);
  margin-left: auto;
  margin-right: auto;
  width: 100%;
}

.welcome-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100%;
  padding: 48px 0 32px;
}

.welcome-content {
  // 欢迎页宽度跟随内容列宽，手机端全宽
  max-width: min(var(--content-max-width, 900px), 100%);
  width: 100%;
  text-align: center;
}

/* Avatar */
.welcome-avatar {
  width: 56px;
  height: 56px;
  margin: 0 auto 20px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;

  .avatar-ring {
    position: absolute;
    inset: 0;
    border-radius: 50%;
    border: 1.5px solid rgba(59, 130, 246, 0.45);
    animation: ring-pulse 3s ease-in-out infinite;
  }

  .avatar-icon {
    font-size: 22px;
    color: #60a5fa;
    line-height: 1;
    text-shadow: 0 0 16px rgba(59, 130, 246, 0.6);
    animation: icon-glow 3s ease-in-out infinite;
  }
}

.welcome-greeting {
  font-size: 22px;
  font-weight: 600;
  color: #f1f5f9;
  margin-bottom: 8px;
  letter-spacing: -0.3px;

  // 宽屏下字体可以稍大
  @media (min-width: 1600px) {
    font-size: 26px;
  }
}

.welcome-agent-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
  margin-bottom: 36px;
  line-height: 1.6;
  max-width: 540px;
  margin-left: auto;
  margin-right: auto;
}

/* Categories */
.categories-wrap {
  display: grid;
  // 手机端单列，平板双列，桌面三列，自动适应
  grid-template-columns: 1fr;
  gap: 12px;
  margin-bottom: 36px;
  text-align: left;

  @media (min-width: 640px) {
    grid-template-columns: repeat(2, 1fr);
    gap: 14px;
  }

  @media (min-width: 960px) {
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
  }
}

.category-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.category-label {
  font-size: 11px;
  font-weight: 600;
  color: rgba(148, 163, 184, 0.7);
  letter-spacing: 0.5px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  margin-bottom: 2px;
}

.category-items {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.prompt-chip {
  cursor: pointer;
  padding: 9px 12px;
  font-size: 12.5px;
  color: rgba(226, 232, 240, 0.75);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 8px;
  line-height: 1.45;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(59, 130, 246, 0.1);
    border-color: rgba(59, 130, 246, 0.3);
    color: #93c5fd;
    transform: translateX(3px);
  }

  &:active {
    transform: translateX(2px) scale(0.99);
  }
}

/* Hint */
.welcome-hint {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: rgba(100, 116, 139, 0.8);
  letter-spacing: 0.2px;

  .hint-dot {
    width: 6px;
    height: 6px;
    background: #10b981;
    border-radius: 50%;
    box-shadow: 0 0 8px #10b981;
    flex-shrink: 0;
    animation: blink 2.5s ease-in-out infinite;
  }
}

@keyframes ring-pulse {
  0%, 100% { opacity: 0.5; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.08); }
}

@keyframes icon-glow {
  0%, 100% { opacity: 0.7; }
  50% { opacity: 1; }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.25; }
}
</style>
