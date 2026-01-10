import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';

/**
 * 路由配置
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/agent',
  },
  {
    path: '/agent',
    name: 'AgentChat',
    component: () => import('@/views/agent/AgentChat.vue'),
    meta: {
      title: 'AI Agent 智能助手',
    },
  },
  {
    path: '/knowledge-bases',
    name: 'KnowledgeBaseList',
    component: () => import('@/views/knowledge-base/KnowledgeBaseList.vue'),
    meta: {
      title: '知识库管理',
    },
  },
  {
    path: '/knowledge-bases/:id',
    name: 'KnowledgeBaseDetail',
    component: () => import('@/views/knowledge-base/KnowledgeBaseDetail.vue'),
    meta: {
      title: '知识库详情',
    },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 路由守卫：设置页面标题
router.beforeEach((to, from, next) => {
  if (to.meta?.title) {
    document.title = `${to.meta.title} - AI Agent`;
  }
  next();
});

export default router;

