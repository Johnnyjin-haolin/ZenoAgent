# 前端适配指南

## 📋 概述

本文档说明如何将前端组件适配到新的独立项目中，包括API路径调整、HTTP工具替换等。

## 🔧 已完成的适配工作

### 1. 创建独立的HTTP工具 ✅

**文件**: `src/utils/http.ts`

- 替代原项目的 `defHttp`
- 支持 GET、POST、PUT、DELETE 请求
- 支持 SSE 流式响应
- 兼容原项目接口格式

### 2. 创建适配版本的API文件 ✅

**文件**: `src/views/agent/agent.api.adapted.ts`

- 已调整为使用新的 HTTP 工具
- API路径已匹配后端
- 标注了后端未实现的接口

## 📝 适配步骤

### 步骤1: 替换API导入

在需要使用Agent API的组件中，将：

```typescript
import { executeAgent, getAvailableModels } from './agent.api';
```

替换为：

```typescript
import { executeAgent, getAvailableModels } from './agent.api.adapted';
```

### 步骤2: 配置HTTP工具路径别名

在 `vite.config.ts` 或 `vue.config.js` 中配置路径别名：

```typescript
// vite.config.ts
export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
});
```

或者在 `tsconfig.json` 中配置：

```json
{
  "compilerOptions": {
    "paths": {
      "@/*": ["src/*"]
    }
  }
}
```

### 步骤3: 配置环境变量

创建 `.env` 文件：

```env
VITE_API_BASE_URL=http://localhost:8080
```

### 步骤4: 安装依赖

确保已安装以下依赖：

```bash
npm install axios
# 或
yarn add axios
```

## 🔍 关键适配点

### 1. HTTP工具替换

**原项目**:
```typescript
import { defHttp } from '/@/utils/http/axios';
```

**新项目**:
```typescript
import { http } from '@/utils/http';
// 或使用兼容导出
import { defHttp } from '@/utils/http';
```

### 2. API路径

API路径已经匹配后端：
- `/aiagent/execute` - 执行Agent任务
- `/aiagent/health` - 健康检查
- `/aiagent/stop/{requestId}` - 停止任务
- `/aiagent/memory/{conversationId}` - 清除记忆

### 3. 响应格式

HTTP工具已处理响应格式转换，保持与原项目兼容。

### 4. SSE流式响应

已实现SSE流式响应处理，与原项目功能一致。

## ⚠️ 注意事项

### 1. 后端未实现的接口

以下接口后端当前未实现，前端使用临时数据或空数组：

- `getAvailableModels()` - 返回默认模型列表
- `getKnowledgeList()` - 返回空数组
- `getConversations()` - 返回空数组
- `getConversationMessages()` - 返回空数组
- 其他会话管理接口 - 返回false或空数组

### 2. 路径别名配置

确保项目配置了 `@` 路径别名指向 `src` 目录。

### 3. 环境变量

需要配置 `VITE_API_BASE_URL` 环境变量，指向后端服务地址。

### 4. CORS配置

确保后端已配置CORS，允许前端访问。

## 🚀 快速适配清单

- [ ] 复制 `src/utils/http.ts` 到项目
- [ ] 复制 `src/views/agent/agent.api.adapted.ts` 到项目
- [ ] 配置路径别名 `@`
- [ ] 配置环境变量 `VITE_API_BASE_URL`
- [ ] 安装依赖 `axios`
- [ ] 替换组件中的API导入
- [ ] 测试API调用

## 📚 相关文件

- `src/utils/http.ts` - HTTP工具实现
- `src/views/agent/agent.api.adapted.ts` - 适配后的API文件
- `backend/src/main/java/com/aiagent/controller/AgentController.java` - 后端API接口

## 🔄 后续工作

1. **后端实现缺失接口**
   - 会话管理接口
   - 模型列表接口
   - 知识库列表接口

2. **优化和测试**
   - 测试API调用
   - 优化错误处理
   - 添加重试机制

---

**提示**: 适配过程中遇到问题时，可以参考原项目的实现或查看后端API文档。


