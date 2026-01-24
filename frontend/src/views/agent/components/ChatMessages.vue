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

    <div v-else class="welcome-message">
      <div class="welcome-icon">🤖</div>
      <h3>欢迎使用 ZenoAgent</h3>
      <p class="welcome-subtitle">企业级 AI Agent，支持 RAG / MCP / 多模型 / 流式过程</p>
      <div class="welcome-capabilities">
        <div v-for="item in capabilityItems" :key="item.title" class="capability-card">
          <div class="capability-icon">{{ item.icon }}</div>
          <div class="capability-title">{{ item.title }}</div>
          <div class="capability-desc">{{ item.desc }}</div>
        </div>
      </div>
      <div class="welcome-scenarios">
        <div class="section-title">场景示例</div>
        <div class="scenario-tags">
          <a-tag
            v-for="prompt in scenarioPrompts"
            :key="prompt"
            class="scenario-tag"
            @click="emit('apply-prompt', prompt)"
          >
            {{ prompt }}
          </a-tag>
        </div>
      </div>
      <p class="welcome-hint">选择模型 → 选择知识库 → 输入问题开始对话</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue';
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
  padding: 16px 20px;
  background: #fff;
  min-height: 0;

  &::-webkit-scrollbar {
    width: 8px;
  }

  &::-webkit-scrollbar-track {
    background: #f5f5f5;
  }

  &::-webkit-scrollbar-thumb {
    background: #d9d9d9;
    border-radius: 4px;

    &:hover {
      background: #bfbfbf;
    }
  }
}

.welcome-message {
  max-width: 600px;
  margin: 30px auto;
  text-align: center;

  .welcome-icon {
    font-size: 48px;
    margin-bottom: 16px;
  }

  h3 {
    font-size: 20px;
    font-weight: 600;
    color: #262626;
    margin-bottom: 12px;
  }

  p {
    font-size: 14px;
    color: #595959;
    margin-bottom: 16px;
  }

  .welcome-subtitle {
    color: #8c8c8c;
    margin-bottom: 18px;
  }

  .welcome-capabilities {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 18px;
  }

  .capability-card {
    text-align: left;
    background: #f7f9fc;
    border: 1px solid #edf1f7;
    border-radius: 10px;
    padding: 12px;
    transition: all 0.2s;

    &:hover {
      border-color: #cfe3ff;
      background: #ffffff;
      box-shadow: 0 4px 12px rgba(24, 144, 255, 0.08);
    }
  }

  .capability-icon {
    font-size: 18px;
    margin-bottom: 6px;
  }

  .capability-title {
    font-size: 14px;
    font-weight: 600;
    color: #262626;
    margin-bottom: 4px;
  }

  .capability-desc {
    font-size: 12px;
    color: #8c8c8c;
    line-height: 1.5;
  }

  .welcome-scenarios {
    margin-bottom: 16px;
    text-align: left;

    .section-title {
      font-size: 13px;
      font-weight: 600;
      color: #262626;
      margin-bottom: 8px;
    }

    .scenario-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  }

  .scenario-tag {
    cursor: pointer;
    border-radius: 16px;
    padding: 2px 10px;
    font-size: 12px;
    color: #1d39c4;
    background: #f0f5ff;
    border: 1px solid #adc6ff;

    &:hover {
      color: #10239e;
      border-color: #85a5ff;
    }
  }

  ul {
    text-align: left;
    list-style: none;
    padding: 0;
    margin-bottom: 20px;

    li {
      padding: 10px 12px;
      margin-bottom: 6px;
      background: #f5f5f5;
      border-radius: 8px;
      font-size: 13px;
      color: #262626;
      line-height: 1.5;
    }
  }

  .welcome-hint {
    color: #8c8c8c;
    font-style: italic;
    font-size: 13px;
  }
}
</style>

