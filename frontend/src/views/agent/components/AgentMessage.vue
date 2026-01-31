<template>
  <div class="agent-message" :class="[message.role, { 'has-error': message.error }]">
    <!-- 头像 -->
    <div class="message-avatar">
      <img v-if="message.role === 'user'" :src="userAvatar" alt="User" />
      <img v-else :src="aiAvatar" alt="AI" />
    </div>

    <!-- 消息内容区 -->
    <div class="message-content-wrapper">
      <!-- 时间和模型信息 -->
      <div class="message-header">
        <span class="message-time">{{ message.datetime }}</span>
        <a-tag v-if="message.model && message.role === 'assistant'" color="blue" size="small">
          {{ message.model }}
        </a-tag>
      </div>

      <!-- 状态提示卡片 -->
      <div
        v-if="showStatusCard"
        class="status-card"
        :class="[message.status]"
      >
        <a-spin :spinning="true" size="small" />
        <span class="status-text">{{ getStatusText }}</span>
      </div>


      <!-- 用户上传的图片 -->
      <div v-if="message.role === 'user' && message.images && message.images.length > 0" class="message-images">
        <div v-for="(img, idx) in message.images" :key="idx" class="image-item">
          <img :src="getImageUrl(img)" alt="图片" @click="handlePreviewImage(img)" />
        </div>
      </div>

      <!-- 执行过程卡片 -->
      <ProcessCard
        v-if="showProcessCard"
        :process="message.process!"
        @toggle-step-expand="handleToggleStepExpand"
        @confirm-tool="handleConfirmTool"
        @reject-tool="handleRejectTool"
      />

      <!-- RAG 检索结果 -->
      <div v-if="showRagResults" class="rag-results">
        <a-collapse ghost>
          <a-collapse-panel key="rag" header="📚 检索到的知识">
            <div v-for="(item, idx) in message.ragResults" :key="idx" class="rag-item">
              <div class="rag-content">
                <a-tooltip :title="item.content">
                  <div class="rag-text">{{ item.content }}</div>
                </a-tooltip>
              </div>
              <div v-if="item.score" class="rag-score">
                <a-tag color="green">相似度: {{ (item.score * 100).toFixed(1) }}%</a-tag>
              </div>
            </div>
          </a-collapse-panel>
        </a-collapse>
      </div>

      <!-- 工具调用 -->
      <div v-if="showToolCalls" class="tool-calls">
        <a-collapse ghost>
          <a-collapse-panel key="tools" header="🔧 工具调用">
            <div v-for="(tool, idx) in message.toolCalls" :key="idx" class="tool-item">
              <div class="tool-header">
                <span class="tool-name">{{ tool.name }}</span>
                <a-tag
                  v-if="tool.status"
                  :color="getToolStatusColor(tool.status)"
                  size="small"
                >
                  {{ getToolStatusText(tool.status) }}
                </a-tag>
              </div>
              <div class="tool-params">
                <div class="tool-label">参数:</div>
                <pre class="tool-data">{{ JSON.stringify(tool.params, null, 2) }}</pre>
              </div>
              <div v-if="tool.result" class="tool-result">
                <div class="tool-label">结果:</div>
                <pre class="tool-data">{{ formatToolResult(tool.result) }}</pre>
              </div>
              <div v-if="tool.error" class="tool-error">
                <a-alert type="error" :message="tool.error" show-icon />
              </div>
            </div>
          </a-collapse-panel>
        </a-collapse>
      </div>

      <!-- 消息正文 -->
      <div v-if="shouldShowMessageBody" class="message-body">
        <div
          v-if="message.role === 'assistant'"
          class="markdown-body"
          :class="{ 'markdown-generating': message.loading }"
          v-html="renderedContent"
        ></div>
        <div v-else class="user-text" v-html="renderedUserContent"></div>
      </div>

      <!-- Token 和耗时统计 -->
      <div v-if="showStats" class="message-stats">
        <span v-if="message.tokens" class="stat-item">
          <Icon icon="ant-design:file-text-outlined" />
          {{ message.tokens }} tokens
        </span>
        <span v-if="message.duration" class="stat-item">
          <Icon icon="ant-design:clock-circle-outlined" />
          {{ formatDuration(message.duration) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Icon } from '@/components/Icon';
import { useUserStore } from '@/store/modules/user';
import { getFileAccessHttpUrl } from '@/utils/common/compUtils';
import { createImgPreview } from '@/components/Preview';
import MarkdownIt from 'markdown-it';
import mdKatex from 'markdown-it-katex';
import mila from 'markdown-it-link-attributes';
import hljs from 'highlight.js';
import defaultAvatar from "@/assets/images/ai/avatar.jpg";
import aiLogo from "@/assets/images/ai/ailogo.png";
import ProcessCard from './ProcessCard.vue';
import type { AgentMessage } from '../agent.types';

// 引入样式
import '@/assets/less/github-markdown.less';
import '@/assets/less/highlight.less';

const props = defineProps<{
  message: AgentMessage;
}>();

const emit = defineEmits<{
  (e: 'confirm-tool'): void;
  (e: 'reject-tool'): void;
}>();

const userStore = useUserStore();

// 用户头像
const userAvatar = computed(() => {
  return getFileAccessHttpUrl(userStore.userInfo?.avatar) || defaultAvatar;
});

// AI 头像
const aiAvatar = computed(() => {
  return aiLogo;
});

// Markdown 渲染器
const mdi = new MarkdownIt({
  html: true,
  linkify: true,
  highlight(code, language) {
    const validLang = !!(language && hljs.getLanguage(language));
    if (validLang) {
      const lang = language ?? '';
      return highlightBlock(hljs.highlight(code, { language: lang }).value, lang);
    }
    return highlightBlock(hljs.highlightAuto(code).value, '');
  },
});

mdi.use(mila, { attrs: { target: '_blank', rel: 'noopener' } });
mdi.use(mdKatex, { 
  throwOnError: false,
  errorColor: '#cc0000'
});

// 代码高亮
function highlightBlock(str: string, lang?: string) {
  return `<pre class="code-block-wrapper"><div class="code-block-header"><span class="code-block-header__lang">${lang}</span><span class="code-block-header__copy">复制代码</span></div><code class="hljs code-block-body ${lang}">${str}</code></pre>`;
}

// 渲染助手消息内容（Markdown）
const renderedContent = computed(() => {
  if (!props.message.content) return '';
  return mdi.render(props.message.content);
});

// 渲染用户消息内容
const renderedUserContent = computed(() => {
  if (!props.message.content) return '';
  return props.message.content.replace(/\n/g, '<br>');
});

// 是否显示执行过程卡片
const showProcessCard = computed(() => {
  return (
    props.message.role === 'assistant' &&
    props.message.process &&
    props.message.process.iterations &&
    props.message.process.iterations.length > 0
  );
});

// 是否显示 RAG 结果（旧版本，已被执行过程取代）
const showRagResults = computed(() => {
  return (
    props.message.ragResults &&
    props.message.ragResults.length > 0 &&
    !props.message.loading &&
    !showProcessCard.value // 如果显示执行过程，则不显示旧版 RAG 结果
  );
});

// 是否显示工具调用（旧版本，已被执行过程取代）
const showToolCalls = computed(() => {
  return (
    props.message.toolCalls &&
    props.message.toolCalls.length > 0 &&
    !showProcessCard.value // 如果显示执行过程，则不显示旧版工具调用
  );
});

// 是否显示统计信息
const showStats = computed(() => {
  return (
    props.message.role === 'assistant' &&
    !props.message.loading &&
    (props.message.tokens || props.message.duration)
  );
});

// 是否显示状态卡片
const showStatusCard = computed(() => {
  // 只有当有明确的 statusText 时才显示状态卡片
  // 或者在 thinking/retrieving/calling_tool 状态下显示
  return (
    props.message.role === 'assistant' &&
    props.message.loading &&
    (props.message.statusText || 
     (props.message.status && 
      ['thinking', 'retrieving', 'calling_tool'].includes(props.message.status)))
  );
});

const handleConfirmTool = () => {
  emit('confirm-tool');
};

const handleRejectTool = () => {
  emit('reject-tool');
};

// 获取状态文本
const getStatusText = computed(() => {
  if (props.message.statusText) {
    return props.message.statusText;
  }
  
  // 默认状态文本
  const statusMap: Record<string, string> = {
    thinking: '思考中...',
    retrieving: '检索知识库...',
    calling_tool: '调用工具中...',
    generating: '正在生成...',
  };
  
  return statusMap[props.message.status || ''] || '处理中...';
});

// 是否显示消息正文
const shouldShowMessageBody = computed(() => {
  // 用户消息：始终显示
  if (props.message.role === 'user') {
    return true;
  }
  
  // 助手消息：
  // 1. 有内容时显示
  // 2. 或者在 generating 状态下显示（即使内容为空，也显示空的消息体准备接收流式内容）
  return props.message.content || props.message.status === 'generating';
});

// 获取图片 URL
function getImageUrl(img: string) {
  return getFileAccessHttpUrl(img);
}

// 预览图片
function handlePreviewImage(img: string) {
  createImgPreview({
    imageList: [getImageUrl(img)],
    defaultWidth: 700,
  });
}

// 工具状态颜色
function getToolStatusColor(status: string) {
  const colorMap: Record<string, string> = {
    pending: 'default',
    success: 'success',
    error: 'error',
  };
  return colorMap[status] || 'default';
}

// 工具状态文本
function getToolStatusText(status: string) {
  const textMap: Record<string, string> = {
    pending: '执行中',
    success: '成功',
    error: '失败',
  };
  return textMap[status] || status;
}

// 格式化工具结果
function formatToolResult(result: any) {
  if (typeof result === 'string') {
    return result;
  }
  return JSON.stringify(result, null, 2);
}

// 格式化耗时
function formatDuration(ms: number) {
  if (ms < 1000) {
    return `${ms}ms`;
  }
  return `${(ms / 1000).toFixed(1)}s`;
}

// 切换步骤展开状态
function handleToggleStepExpand(stepId: string) {
  if (props.message.process && props.message.process.iterations) {
    // 遍历所有迭代，查找对应的步骤
    for (const iteration of props.message.process.iterations) {
      if (iteration.steps) {
        const step = iteration.steps.find((s) => s.id === stepId);
        if (step) {
          step.expanded = !step.expanded;
          return;
        }
      }
    }
  }
}
</script>

<style scoped lang="less">
.agent-message {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  animation: fadeIn 0.3s ease-in;

  &.user {
    flex-direction: row-reverse;

    .message-content-wrapper {
      align-items: flex-end;
    }

    .message-body {
      background: #0052CC;
      color: #fff;

      .user-text {
        line-height: 1.6;
      }
    }
  }

  &.assistant {
    .message-body {
      background: #f4f6f8;
    }
  }

  &.has-error {
    .message-body {
      background: #fff2f0;
      border: 1px solid #ffccc7;
    }
  }
}

.message-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;

  img {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    object-fit: cover;
  }
}

.message-content-wrapper {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #8c8c8c;
}

.status-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  background: #f0f2f5;
  font-size: 13px;
  animation: pulse 1.5s ease-in-out infinite;

  &.thinking {
    background: #e6f7ff;
    border: 1px solid #91d5ff;
  }

  &.retrieving {
    background: #f6ffed;
    border: 1px solid #b7eb8f;
  }

  &.calling_tool {
    background: #fff7e6;
    border: 1px solid #ffd591;
  }

  .status-text {
    color: #595959;
  }
}


.message-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  .image-item {
    width: 120px;
    height: 80px;
    cursor: pointer;
    border-radius: 4px;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 0.2s;

      &:hover {
        transform: scale(1.05);
      }
    }
  }
}

.rag-results,
.tool-calls {
  :deep(.ant-collapse) {
    background: transparent;

    .ant-collapse-item {
      border: none;
    }

    .ant-collapse-header {
      padding: 8px 12px;
      background: #fafafa;
      border-radius: 6px;
      font-weight: 500;
      font-size: 13px;
    }

    .ant-collapse-content {
      background: transparent;
    }
  }
}

.rag-item {
  padding: 8px;
  margin-bottom: 8px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 4px;

  .rag-content {
    margin-bottom: 4px;
  }

  .rag-text {
    font-size: 13px;
    color: #595959;
    line-height: 1.6;
    max-height: 100px;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.tool-item {
  padding: 12px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;

  .tool-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  .tool-name {
    font-weight: 500;
    color: #262626;
  }

  .tool-label {
    font-size: 12px;
    color: #8c8c8c;
    margin-bottom: 4px;
  }

  .tool-data {
    padding: 8px;
    background: #fafafa;
    border: 1px solid #f0f0f0;
    border-radius: 4px;
    font-size: 12px;
    margin: 0;
    overflow-x: auto;
  }

  .tool-params,
  .tool-result {
    margin-top: 8px;
  }

  .tool-error {
    margin-top: 8px;
  }
}

.message-body {
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;

  .markdown-body {
    background: transparent;
  }

  .markdown-generating::after {
    content: '▋';
    animation: blink 1s steps(2) infinite;
    margin-left: 2px;
  }
}

.message-stats {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #8c8c8c;
  padding-top: 4px;

  .stat-item {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.8;
  }
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}
</style>

