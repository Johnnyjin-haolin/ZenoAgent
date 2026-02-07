# AI Agent 前端项目

## 📁 项目结构

```
frontend/src/views/agent/
├── AgentChat.vue              # 主聊天组件
├── agent.api.ts               # API封装
├── agent.types.ts             # TypeScript类型定义
├── README.md                  # 说明文档
├── components/                # 子组件
│   ├── AgentAssistant.vue
│   ├── AgentKnowledgeSelector.vue
│   ├── AgentMessage.vue
│   ├── AgentModelSelector.vue
│   ├── AgentSlide.vue
│   ├── AgentToolConfig.vue
│   ├── ProcessCard.vue
│   ├── ProcessStep.vue
│   └── animations/            # Lottie动画文件
│       ├── click.json
│       ├── drag.json
│       ├── hover.json
│       ├── idle.json
│       └── thinking.json
└── hooks/                     # Vue Composition API Hooks
    └── useAgentChat.ts
```

## 🔧 需要适配的内容

### 1. API路径调整

前端组件中的API路径需要根据后端实际路径进行调整。

**原项目API路径**:
```typescript
export enum AgentApi {
  execute = '/aiagent/execute',
  availableModels = '/aiagent/models/available',
  // ...
}
```

**新项目可能需要调整为**:
```typescript
export enum AgentApi {
  execute = '/api/agent/execute',
  availableModels = '/api/agent/models/available',
  // 根据后端实际路径调整
}
```

### 2. HTTP请求工具

HTTP 请求工具使用说明：

**选项1：创建独立的HTTP工具**
```typescript
// utils/http.ts
import axios from 'axios';

export const http = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080',
  timeout: 30000,
});
```

**选项2：使用现有的HTTP工具**
如果前端项目已有HTTP封装，替换`defHttp`为对应的工具。

### 3. 依赖检查

确保以下依赖可用：
- Vue 3
- Ant Design Vue
- axios (或类似的HTTP客户端)
- @iconify/vue (图标库)
- lottie-web (动画库，如果需要)

### 4. 路由配置

将组件添加到路由配置：
```typescript
{
  path: '/agent',
  name: 'AgentChat',
  component: () => import('@/views/agent/AgentChat.vue'),
}
```

## 📝 适配步骤

### 步骤1：调整API路径

编辑 `agent.api.ts`，根据后端实际路径调整API端点。

### 步骤2：替换HTTP工具

1. 创建或使用现有的HTTP请求工具
2. 在 `agent.api.ts` 中替换 `defHttp` 为新工具

### 步骤3：检查类型定义

确保 `agent.types.ts` 中的类型定义与后端API响应格式匹配。

### 步骤4：测试组件

1. 编译前端项目
2. 测试组件加载
3. 测试API调用
4. 测试完整功能

## 🚀 快速开始

### 1. 安装依赖

```bash
cd frontend
npm install
# 或
yarn install
```

### 2. 配置环境变量

创建 `.env` 文件：
```env
VUE_APP_API_BASE_URL=http://localhost:8080
```

### 3. 启动开发服务器

```bash
npm run serve
# 或
yarn serve
```

## 🔍 关键文件说明

### AgentChat.vue

主聊天界面组件，包含：
- 消息显示区域
- 输入框
- 配置抽屉
- 会话列表

### agent.api.ts

API封装，包含：
- API端点定义
- HTTP请求封装
- SSE流式响应处理
- 各种API方法

### agent.types.ts

TypeScript类型定义，包含：
- Agent请求参数
- Agent事件类型
- 消息类型
- 配置类型

### useAgentChat.ts

Vue Composition API Hook，包含：
- 消息状态管理
- SSE事件处理
- 执行步骤管理
- 工具调用处理

## 📚 参考文档

- 后端API文档：参考 `../backend/README.md`
- API路径配置：参考后端 `AgentController.java`
- 技术方案：参考 `../TECHNICAL_PLAN.md`

## ⚠️ 注意事项

1. **API路径**: 需要根据后端实际路径调整
2. **HTTP工具**: 需要替换`defHttp`为项目中的HTTP工具
3. **依赖**: 确保所有依赖都已安装
4. **CORS**: 后端需要配置CORS以允许前端访问
5. **环境变量**: 需要配置API基础URL

## ✅ 验证清单

- [ ] API路径已调整
- [ ] HTTP工具已替换
- [ ] 类型定义已检查
- [ ] 依赖已安装
- [ ] 组件可以编译
- [ ] API调用正常
- [ ] SSE流式响应正常
- [ ] 动画文件加载正常

---

**提示**: 如有问题，请参考原项目实现或查看后端API文档。


