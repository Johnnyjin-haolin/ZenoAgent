<template>
  <div>
    <!-- 可拖拽的浮动机器人 -->
    <div
      v-if="visible"
      ref="assistantRef"
      class="agent-assistant-robot"
      :class="{ 
        'is-dragging': dragging, 
        'is-hover': isHovering, 
        'is-thinking': agentWorking,
        'is-clicking': isClicking 
      }"
      :style="assistantStyle"
      @mousedown="handleMouseDown"
      @mouseenter="handleMouseEnter"
      @mouseleave="handleMouseLeave"
      @click="handleClick"
      @contextmenu.prevent="handleRightClick"
    >
      <div class="robot-container">
        <!-- 悬停提示气泡 -->
        <transition name="bubble-fade">
          <div v-if="isHovering && !dragging && !showCloseConfirm" class="chat-bubble">
            <div class="bubble-content">
              <span class="bubble-text">有什么问题都可以来问我呀</span>
              <div class="bubble-emoji">😊</div>
            </div>
            <div class="bubble-arrow"></div>
          </div>
        </transition>
        
        <!-- 机器人身体 -->
        <div class="robot-body">
          <!-- 天线 -->
          <div class="antenna">
            <div class="antenna-rod"></div>
            <div class="antenna-light"></div>
          </div>
          
          <!-- 眼睛 -->
          <div class="eyes">
            <div class="eye left"></div>
            <div class="eye right"></div>
          </div>
          
          <!-- 嘴巴 -->
          <div class="mouth"></div>
          
          <!-- 手臂（悬停时显示） -->
          <div class="arm left-arm"></div>
          <div class="arm right-arm"></div>
          
          <!-- 汗滴（拖拽时显示） -->
          <div class="sweat-drop"></div>
        </div>
        
        <!-- 思考状态的问号 -->
        <transition name="question-fade">
          <div v-if="agentWorking" class="question-mark">?</div>
        </transition>
        
        <!-- 点击特效 -->
        <transition name="sparkle-fade">
          <div v-if="showClickEffect" class="click-effect">
            <div class="sparkle" v-for="i in 8" :key="i" :style="getSparkleStyle(i)"></div>
          </div>
        </transition>
      </div>
      
      <!-- 自定义关闭确认弹窗 -->
      <transition name="confirm-fade">
        <div v-if="showCloseConfirm" class="close-confirm-overlay" @click.self="cancelClose">
          <div class="close-confirm-dialog">
            <div class="confirm-icon">
              <div class="icon-wrapper">
                <Icon icon="ant-design:exclamation-circle-outlined" :size="48" />
              </div>
            </div>
            <h3 class="confirm-title">确认关闭助手？</h3>
            <p class="confirm-message">关闭后可以在系统设置中重新开启</p>
            <div class="confirm-actions">
              <button class="btn-cancel" @click="cancelClose">
                <Icon icon="ant-design:close-outlined" :size="16" />
                取消
              </button>
              <button class="btn-confirm" @click="handleClose">
                <Icon icon="ant-design:check-outlined" :size="16" />
                确认关闭
              </button>
            </div>
          </div>
        </div>
      </transition>
    </div>

    <!-- Agent 聊天抽屉 -->
    <a-drawer
      v-model:open="drawerVisible"
      title="AI Agent 智能助手"
      placement="right"
      :width="800"
      :closable="true"
      :body-style="{ padding: 0, height: '100%', overflow: 'hidden' }"
      :header-style="{ padding: '16px 24px', borderBottom: '1px solid #f0f0f0' }"
      :z-index="9999"
    >
      <div style="height: 100%; display: flex; flex-direction: column; overflow: hidden;">
        <AgentChat v-if="drawerVisible" />
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, onBeforeUnmount } from 'vue';
import { Icon } from '/@/components/Icon';
import { getAuthCache, setAuthCache } from '/@/utils/auth';
import AgentChat from '../AgentChat.vue';

// 常量定义
const STORAGE_KEY = 'AGENT_ASSISTANT_POSITION';
const CLOSE_FLAG_KEY = 'AGENT_ASSISTANT_CLOSED';

// 组件引用
const assistantRef = ref<HTMLElement | null>(null);

// 状态管理
const visible = ref(true);
const drawerVisible = ref(false);
const showCloseConfirm = ref(false);
const isHovering = ref(false);
const isClicking = ref(false);
const showClickEffect = ref(false);

// 位置状态（默认右下角）
const position = reactive({
  x: 0,
  y: 0,
});

// 拖拽状态
const dragging = ref(false);
const dragStart = reactive({ x: 0, y: 0 });
const dragMoved = ref(false);

// Agent 工作状态
const agentWorking = ref(false);

// 计算属性：浮动元素样式
const assistantStyle = computed(() => ({
  left: `${position.x}px`,
  top: `${position.y}px`,
  cursor: dragging.value ? 'grabbing' : 'grab',
}));

// 计算星星位置
const getSparkleStyle = (index: number) => {
  const angle = (index - 1) * 45;
  const distance = 50;
  const radian = (angle * Math.PI) / 180;
  const x = Math.cos(radian) * distance;
  const y = Math.sin(radian) * distance;
  
  return {
    left: `calc(50% + ${x}px)`,
    top: `calc(50% + ${y}px)`,
    animationDelay: `${index * 0.05}s`,
  };
};

// 初始化位置
const initPosition = () => {
  const savedPosition = getAuthCache(STORAGE_KEY);
  if (savedPosition && savedPosition.x !== undefined && savedPosition.y !== undefined) {
    position.x = savedPosition.x;
    position.y = savedPosition.y;
  } else {
    position.x = window.innerWidth - 100;
    position.y = window.innerHeight - 120;
  }
};

// 保存位置
const savePosition = () => {
  setAuthCache(STORAGE_KEY, { x: position.x, y: position.y });
};

// 初始化
onMounted(() => {
  const closedFlag = getAuthCache(CLOSE_FLAG_KEY);
  if (closedFlag) {
    visible.value = false;
    return;
  }

  initPosition();
  window.addEventListener('resize', handleWindowResize);
});

// 清理
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleWindowResize);
});

// 窗口大小变化处理
const handleWindowResize = () => {
  const maxX = window.innerWidth - 80;
  const maxY = window.innerHeight - 80;
  
  if (position.x > maxX) {
    position.x = maxX;
  }
  if (position.y > maxY) {
    position.y = maxY;
  }
  
  savePosition();
};

// 鼠标进入
const handleMouseEnter = () => {
  if (!dragging.value && !isClicking.value) {
    isHovering.value = true;
  }
};

// 鼠标离开
const handleMouseLeave = () => {
  isHovering.value = false;
};

// 左键点击事件
const handleClick = () => {
  if (dragMoved.value || showCloseConfirm.value) {
    return;
  }

  isClicking.value = true;
  showClickEffect.value = true;
  
  setTimeout(() => {
    isClicking.value = false;
    showClickEffect.value = false;
  }, 800);
  
  setTimeout(() => {
    drawerVisible.value = true;
  }, 300);
};

// 右键点击事件
const handleRightClick = () => {
  showCloseConfirm.value = true;
  isHovering.value = false;
};

// 取消关闭
const cancelClose = () => {
  showCloseConfirm.value = false;
};

// 拖拽开始
const handleMouseDown = (e: MouseEvent) => {
  if (e.button !== 0 || showCloseConfirm.value) return;
  
  e.preventDefault();
  e.stopPropagation();
  
  dragging.value = true;
  dragMoved.value = false;
  isHovering.value = false;
  
  dragStart.x = e.clientX - position.x;
  dragStart.y = e.clientY - position.y;

  document.addEventListener('mousemove', handleMouseMove);
  document.addEventListener('mouseup', handleMouseUp);
};

// 拖拽中
const handleMouseMove = (e: MouseEvent) => {
  if (!dragging.value) return;
  
  const newX = e.clientX - dragStart.x;
  const newY = e.clientY - dragStart.y;
  
  const moved = Math.abs(newX - position.x) > 5 || Math.abs(newY - position.y) > 5;
  if (moved) {
    dragMoved.value = true;
  }
  
  const maxX = window.innerWidth - 80;
  const maxY = window.innerHeight - 80;
  
  position.x = Math.max(0, Math.min(newX, maxX));
  position.y = Math.max(0, Math.min(newY, maxY));
};

// 拖拽结束
const handleMouseUp = () => {
  dragging.value = false;
  
  document.removeEventListener('mousemove', handleMouseMove);
  document.removeEventListener('mouseup', handleMouseUp);
  
  savePosition();
  
  setTimeout(() => {
    dragMoved.value = false;
  }, 100);
};

// 关闭助手
const handleClose = () => {
  visible.value = false;
  showCloseConfirm.value = false;
  setAuthCache(CLOSE_FLAG_KEY, true);
};
</script>

<style scoped lang="less">
.agent-assistant-robot {
  position: fixed;
  width: 80px;
  height: 80px;
  z-index: 9998;
  user-select: none;
  transition: opacity 0.3s ease;
  
  .robot-container {
    position: relative;
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  
  // 聊天气泡
  .chat-bubble {
    position: absolute;
    left: -220px;
    top: 50%;
    transform: translateY(-50%);
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-radius: 16px;
    padding: 12px 16px;
    box-shadow: 0 4px 20px rgba(102, 126, 234, 0.4),
                0 0 0 3px rgba(255, 255, 255, 0.9);
    z-index: 1;
    animation: bubble-bounce 0.5s cubic-bezier(0.68, -0.55, 0.265, 1.55);
    
    .bubble-content {
      display: flex;
      align-items: center;
      gap: 8px;
      white-space: nowrap;
      
      .bubble-text {
        color: #fff;
        font-size: 14px;
        font-weight: 500;
        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
      }
      
      .bubble-emoji {
        font-size: 18px;
        animation: emoji-bounce 1s ease-in-out infinite;
      }
    }
    
    .bubble-arrow {
      position: absolute;
      right: -8px;
      top: 50%;
      transform: translateY(-50%);
      width: 0;
      height: 0;
      border-left: 10px solid #764ba2;
      border-top: 8px solid transparent;
      border-bottom: 8px solid transparent;
    }
  }
  
  .robot-body {
    position: relative;
    width: 70px;
    height: 70px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-radius: 20px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 20px rgba(102, 126, 234, 0.4),
                0 0 0 4px rgba(255, 255, 255, 0.9),
                0 0 20px rgba(102, 126, 234, 0.3);
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    animation: float 3s ease-in-out infinite;
    
    // 天线
    .antenna {
      position: absolute;
      top: -18px;
      left: 50%;
      transform: translateX(-50%);
      display: flex;
      flex-direction: column;
      align-items: center;
      animation: antenna-wiggle 3s ease-in-out infinite;
      
      .antenna-rod {
        width: 3px;
        height: 12px;
        background: linear-gradient(180deg, #764ba2 0%, #5a67d8 100%);
        border-radius: 2px;
      }
      
      .antenna-light {
        width: 10px;
        height: 10px;
        background: radial-gradient(circle, #ff6b6b 0%, #ee5a6f 100%);
        border-radius: 50%;
        margin-top: 2px;
        animation: blink 2s ease-in-out infinite;
        box-shadow: 0 0 10px rgba(245, 101, 101, 0.8),
                    0 0 20px rgba(245, 101, 101, 0.4);
      }
    }
    
    // 眼睛
    .eyes {
      display: flex;
      gap: 16px;
      margin-top: 20px;
      
      .eye {
        width: 10px;
        height: 10px;
        background: #2d3748;
        border-radius: 50%;
        transition: all 0.3s ease;
        animation: blink-eyes 4s ease-in-out infinite;
        
        &.left {
          animation-delay: 0.1s;
        }
        
        &.right {
          animation-delay: 0.15s;
        }
      }
    }
    
    // 嘴巴
    .mouth {
      width: 20px;
      height: 10px;
      border: 2px solid #2d3748;
      border-top: none;
      border-radius: 0 0 10px 10px;
      margin-top: 8px;
      transition: all 0.3s ease;
    }
    
    // 手臂
    .arm {
      position: absolute;
      width: 8px;
      height: 24px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-radius: 4px;
      opacity: 0;
      transform-origin: top center;
      transition: all 0.3s ease;
      box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
      
      &.left-arm {
        left: -10px;
        top: 28px;
      }
      
      &.right-arm {
        right: -10px;
        top: 28px;
      }
    }
    
    // 汗滴
    .sweat-drop {
      position: absolute;
      right: 8px;
      top: 12px;
      width: 8px;
      height: 10px;
      background: linear-gradient(135deg, #4299e1 0%, #3182ce 100%);
      border-radius: 50% 50% 50% 0;
      opacity: 0;
      transform: rotate(-45deg);
      transition: all 0.3s ease;
      animation: sweat-fall 1s ease-in-out infinite;
    }
  }
  
  // 悬停状态
  &.is-hover {
    .robot-body {
      transform: scale(1.15);
      box-shadow: 0 6px 30px rgba(102, 126, 234, 0.6),
                  0 0 0 5px rgba(255, 255, 255, 0.95),
                  0 0 30px rgba(102, 126, 234, 0.5);
      animation: float 2s ease-in-out infinite, wiggle 0.5s ease-in-out;
      
      .eyes .eye {
        width: 12px;
        height: 4px;
        border-radius: 0 0 6px 6px;
        background: #2d3748;
      }
      
      .mouth {
        width: 26px;
        height: 13px;
        border-radius: 0 0 13px 13px;
      }
      
      .arm {
        opacity: 1;
        
        &.left-arm {
          animation: wave-left 0.6s ease-in-out infinite;
        }
        
        &.right-arm {
          animation: wave-right 0.6s ease-in-out infinite;
          animation-delay: 0.3s;
        }
      }
    }
  }
  
  // 点击状态
  &.is-clicking {
    .robot-body {
      animation: jump 0.8s cubic-bezier(0.68, -0.55, 0.265, 1.55);
      
      .eyes .eye {
        width: 14px;
        height: 14px;
        background: radial-gradient(circle, #ffd700 0%, #ffed4e 100%);
        box-shadow: 0 0 10px rgba(255, 215, 0, 0.8);
      }
      
      .mouth {
        width: 30px;
        height: 15px;
      }
    }
  }
  
  // 拖拽状态
  &.is-dragging {
    .robot-body {
      transform: scale(0.9) rotate(-8deg);
      box-shadow: 0 2px 10px rgba(102, 126, 234, 0.3),
                  0 0 0 3px rgba(255, 255, 255, 0.7),
                  0 0 15px rgba(102, 126, 234, 0.2);
      animation: shake 0.5s ease-in-out infinite;
      
      .eyes .eye {
        width: 8px;
        height: 14px;
        border-radius: 50%;
      }
      
      .mouth {
        width: 12px;
        height: 12px;
        border-radius: 50%;
        border: 2px solid #2d3748;
      }
      
      .sweat-drop {
        opacity: 1;
      }
    }
  }
  
  // 思考状态
  &.is-thinking {
    .robot-body {
      animation: float 2s ease-in-out infinite, thinking-tilt 4s ease-in-out infinite;
      
      .eyes .eye {
        width: 8px;
        height: 12px;
        transform: translateY(-2px);
      }
      
      .mouth {
        width: 16px;
        height: 6px;
      }
    }
  }
  
  // 问号
  .question-mark {
    position: absolute;
    top: -40px;
    left: 50%;
    transform: translateX(-50%);
    font-size: 28px;
    font-weight: bold;
    color: #667eea;
    animation: float-question 2s ease-in-out infinite;
    text-shadow: 0 2px 8px rgba(102, 126, 234, 0.6),
                 0 0 20px rgba(102, 126, 234, 0.4);
    filter: drop-shadow(0 0 10px rgba(102, 126, 234, 0.8));
  }
  
  // 点击特效
  .click-effect {
    position: absolute;
    top: 50%;
    left: 50%;
    width: 100%;
    height: 100%;
    pointer-events: none;
    
    .sparkle {
      position: absolute;
      width: 10px;
      height: 10px;
      background: radial-gradient(circle, #ffd700 0%, #ff69b4 100%);
      border-radius: 50%;
      box-shadow: 0 0 10px rgba(255, 215, 0, 0.8),
                  0 0 20px rgba(255, 105, 180, 0.6);
      animation: sparkle-out 0.8s ease-out forwards;
      transform: translate(-50%, -50%);
    }
  }
  
  // 关闭确认弹窗
  .close-confirm-overlay {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.5);
    backdrop-filter: blur(4px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 10000;
    
    .close-confirm-dialog {
      background: #fff;
      border-radius: 16px;
      padding: 32px;
      width: 360px;
      box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2),
                  0 0 0 1px rgba(0, 0, 0, 0.05);
      animation: dialog-scale-in 0.3s cubic-bezier(0.68, -0.55, 0.265, 1.55);
      
      .confirm-icon {
        display: flex;
        justify-content: center;
        margin-bottom: 20px;
        
        .icon-wrapper {
          width: 64px;
          height: 64px;
          background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #fff;
          box-shadow: 0 4px 20px rgba(251, 191, 36, 0.4);
          animation: icon-pulse 2s ease-in-out infinite;
        }
      }
      
      .confirm-title {
        font-size: 20px;
        font-weight: 600;
        color: #1f2937;
        text-align: center;
        margin: 0 0 12px 0;
      }
      
      .confirm-message {
        font-size: 14px;
        color: #6b7280;
        text-align: center;
        margin: 0 0 24px 0;
        line-height: 1.6;
      }
      
      .confirm-actions {
        display: flex;
        gap: 12px;
        
        button {
          flex: 1;
          height: 40px;
          border: none;
          border-radius: 8px;
          font-size: 14px;
          font-weight: 500;
          cursor: pointer;
          transition: all 0.2s ease;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 6px;
          
          &.btn-cancel {
            background: #f3f4f6;
            color: #374151;
            
            &:hover {
              background: #e5e7eb;
              transform: translateY(-1px);
            }
            
            &:active {
              transform: translateY(0);
            }
          }
          
          &.btn-confirm {
            background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
            color: #fff;
            box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
            
            &:hover {
              box-shadow: 0 4px 12px rgba(239, 68, 68, 0.4);
              transform: translateY(-1px);
            }
            
            &:active {
              transform: translateY(0);
            }
          }
        }
      }
    }
  }
}

// 过渡动画
.bubble-fade-enter-active,
.bubble-fade-leave-active {
  transition: all 0.3s ease;
}

.bubble-fade-enter-from {
  opacity: 0;
  transform: translateY(-50%) translateX(-10px);
}

.bubble-fade-leave-to {
  opacity: 0;
  transform: translateY(-50%) translateX(-10px);
}

.question-fade-enter-active,
.question-fade-leave-active {
  transition: all 0.3s ease;
}

.question-fade-enter-from {
  opacity: 0;
  transform: translateX(-50%) translateY(10px) scale(0.5);
}

.question-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-10px) scale(0.5);
}

.sparkle-fade-enter-active {
  transition: opacity 0.2s ease;
}

.sparkle-fade-enter-from {
  opacity: 0;
}

.confirm-fade-enter-active,
.confirm-fade-leave-active {
  transition: all 0.3s ease;
}

.confirm-fade-enter-from,
.confirm-fade-leave-to {
  opacity: 0;
}

// 动画定义
@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

@keyframes bubble-bounce {
  0% {
    transform: translateY(-50%) scale(0.8);
    opacity: 0;
  }
  60% {
    transform: translateY(-50%) scale(1.05);
  }
  100% {
    transform: translateY(-50%) scale(1);
    opacity: 1;
  }
}

@keyframes emoji-bounce {
  0%, 100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-3px) scale(1.1);
  }
}

@keyframes blink {
  0%, 88%, 92%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  90% {
    opacity: 0.3;
    transform: scale(0.8);
  }
}

@keyframes blink-eyes {
  0%, 88%, 94%, 100% {
    transform: scaleY(1);
  }
  91% {
    transform: scaleY(0.1);
  }
}

@keyframes wave-left {
  0%, 100% {
    transform: rotate(-30deg);
  }
  50% {
    transform: rotate(30deg);
  }
}

@keyframes wave-right {
  0%, 100% {
    transform: rotate(30deg);
  }
  50% {
    transform: rotate(-30deg);
  }
}

@keyframes wiggle {
  0%, 100% {
    transform: scale(1.15) rotate(0deg);
  }
  25% {
    transform: scale(1.15) rotate(-5deg);
  }
  75% {
    transform: scale(1.15) rotate(5deg);
  }
}

@keyframes jump {
  0% {
    transform: translateY(0) scale(1);
  }
  30% {
    transform: translateY(-20px) scale(1.1, 0.9);
  }
  50% {
    transform: translateY(-30px) scale(1.05, 0.95);
  }
  70% {
    transform: translateY(-20px) scale(0.95, 1.05);
  }
  100% {
    transform: translateY(0) scale(1);
  }
}

@keyframes shake {
  0%, 100% {
    transform: scale(0.9) rotate(-8deg) translateX(0);
  }
  25% {
    transform: scale(0.9) rotate(-10deg) translateX(-2px);
  }
  75% {
    transform: scale(0.9) rotate(-6deg) translateX(2px);
  }
}

@keyframes thinking-tilt {
  0%, 100% {
    transform: rotate(0deg);
  }
  25% {
    transform: rotate(8deg);
  }
  75% {
    transform: rotate(-8deg);
  }
}

@keyframes antenna-wiggle {
  0%, 100% {
    transform: translateX(-50%) rotate(0deg);
  }
  33% {
    transform: translateX(-50%) rotate(-8deg);
  }
  66% {
    transform: translateX(-50%) rotate(8deg);
  }
}

@keyframes float-question {
  0%, 100% {
    transform: translateX(-50%) translateY(0) rotate(-10deg);
    opacity: 1;
  }
  50% {
    transform: translateX(-50%) translateY(-12px) rotate(10deg);
    opacity: 0.9;
  }
}

@keyframes sweat-fall {
  0% {
    transform: rotate(-45deg) translateY(0);
    opacity: 0.8;
  }
  100% {
    transform: rotate(-45deg) translateY(10px);
    opacity: 0;
  }
}

@keyframes sparkle-out {
  0% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 1;
  }
  100% {
    transform: translate(-50%, -50%) scale(3);
    opacity: 0;
  }
}

@keyframes dialog-scale-in {
  0% {
    transform: scale(0.7);
    opacity: 0;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

@keyframes icon-pulse {
  0%, 100% {
    transform: scale(1);
    box-shadow: 0 4px 20px rgba(251, 191, 36, 0.4);
  }
  50% {
    transform: scale(1.05);
    box-shadow: 0 6px 30px rgba(251, 191, 36, 0.6);
  }
}
</style>
