# AI Agent 智能助手

## 📋 概述

AI Agent 智能助手是一个基于后端 Agent 服务的前端交互界面，提供智能任务理解、动态模型选择、知识库增强、工具编排等功能。

## 🚀 功能特性

- ✅ **智能任务理解** - 自动分类用户意图（闲聊/知识查询/工具调用/复杂任务）
- ✅ **动态模型选择** - 根据任务自动选择最优模型（或用户手动指定）
- ✅ **知识库增强** - 支持选择多个知识库进行检索
- ✅ **工具编排调用** - 可调用 MCP 工具（设备查询、命令执行等）
- ✅ **流式实时反馈** - SSE 流式显示 AI 思考过程和执行进度
- ✅ **多会话管理** - 支持多个对话会话，上下文记忆

## 📁 目录结构

```
agent/
├── AgentChat.vue              # 主聊天页面
├── agent.api.ts               # API 封装（SSE 事件处理）
├── agent.types.ts             # TypeScript 类型定义
├── hooks/
│   └── useAgentChat.ts        # Agent 聊天逻辑 Hook
├── components/
│   ├── AgentMessage.vue       # 消息组件
│   ├── AgentModelSelector.vue # 模型选择器
│   ├── AgentKnowledgeSelector.vue # 知识库选择器
│   ├── AgentToolConfig.vue    # 工具配置
│   └── AgentSlide.vue         # 会话列表
└── README.md                  # 本文件
```

## 🔧 技术实现

### 1. SSE 事件处理

后端通过 SSE（Server-Sent Events）推送以下事件：

| 事件类型 | 说明 | 触发时机 |
|---------|------|---------|
| `agent:start` | 任务开始 | 开始处理请求 |
| `agent:thinking` | AI 思考中 | 分析任务类型 |
| `agent:model_selected` | 模型已选择 | 选择完模型 |
| `agent:rag_retrieve` | RAG 检索中 | 检索知识库 |
| `agent:tool_call` | 工具调用中 | 调用工具前 |
| `agent:tool_result` | 工具执行结果 | 工具执行完成 |
| `agent:message` | 流式内容 | 生成回答内容（多次） |
| `agent:complete` | 任务完成 | 全部完成 |
| `agent:error` | 错误发生 | 发生错误 |

### 2. 核心 Hook

`useAgentChat` Hook 封装了聊天的核心逻辑：

```typescript
const {
  messages,        // 消息列表
  loading,         // 加载状态
  currentStatus,   // 当前状态
  sendMessage,     // 发送消息
  stopGeneration,  // 停止生成
  clearMessages,   // 清空消息
} = useAgentChat({
  conversationId,
  defaultModelId,
  defaultKnowledgeIds,
  defaultEnabledTools,
});
```

### 3. 消息数据结构

```typescript
interface AgentMessage {
  id: string;                    // 消息ID
  role: 'user' | 'assistant';    // 角色
  content: string;               // 消息内容
  datetime: string;              // 时间
  status?: 'thinking' | 'retrieving' | 'calling_tool' | 'generating' | 'done' | 'error';
  statusText?: string;           // 状态文本
  toolCalls?: ToolCall[];        // 工具调用记录
  ragResults?: RagResult[];      // RAG 检索结果
  model?: string;                // 使用的模型
  tokens?: number;               // Token 使用量
  duration?: number;             // 耗时
}
```

## 🎯 使用方式

### 访问路径

```
http://localhost:3100/#/ai/agent
```

### 基本操作

1. **发送消息**
   - 在底部输入框输入问题
   - 按 `Enter` 发送，`Shift + Enter` 换行

2. **配置 Agent**
   - 点击右上角"配置"按钮
   - 选择模型、知识库、工具
   - 设置执行模式

3. **管理会话**
   - 左侧会话列表查看历史对话
   - 点击"新对话"创建新会话
   - 点击会话项切换对话

4. **停止生成**
   - 点击"停止"按钮中断生成

## 📡 后端接口

### 主要接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 执行 Agent 任务 | POST | `/aiagent/execute` | SSE 流式返回 |
| 获取可用模型 | GET | `/aiagent/models/available` | 获取模型列表 |
| 健康检查 | GET | `/aiagent/health` | 检查服务状态 |

### 请求示例

```typescript
const request: AgentRequest = {
  content: "请帮我查询设备配置相关的文档",
  conversationId: "conv-20251130-001",
  modelId: "platform-gpt4o-mini",
  knowledgeIds: ["kb-device-001", "kb-config-002"],
  enabledTools: ["device-*", "query-*"],
  mode: "AUTO",
};
```

## 🔍 状态展示

### 思考中
显示 AI 正在分析任务的状态

### 检索中
显示正在检索知识库的进度，完成后展示检索结果

### 工具调用
显示工具调用的名称、参数和结果

### 生成中
流式显示生成的内容

### 完成
显示 Token 使用量和耗时统计

## ⚙️ 配置项

### 模型选择

- **智能选择（推荐）** - 根据任务类型自动选择
- **标准版** - GPT-4o Mini，快速响应
- **专业版** - GPT-4o，强大能力

### 知识库

可选择一个或多个知识库进行检索

### 工具配置

- 支持通配符（如 `device-*` 表示所有设备工具）
- 留空表示允许所有工具
- 预设常用工具选项

### 执行模式

- **自动模式** - AI 自主决策工具调用
- **手动模式** - 需要确认后执行工具

## 🎨 样式说明

- 用户消息：蓝色背景，右对齐
- AI 消息：灰色背景，左对齐
- 状态卡片：不同状态不同颜色（思考-蓝、检索-绿、工具-橙）
- 工具调用：可展开折叠，显示参数和结果
- RAG 结果：可展开折叠，显示检索到的知识

## 🐛 问题排查

### 无法连接后端
1. 检查后端服务是否启动
2. 访问 `/aiagent/health` 检查服务状态
3. 查看浏览器控制台错误信息

### SSE 连接断开
1. 检查网络连接
2. 查看后端日志
3. 增加超时时间（当前 5 分钟）

### 模型列表为空
1. 检查后端是否配置了平台模型
2. 确认 `init_platform_models.sql` 是否已执行
3. 检查租户权限配置

## 📝 开发说明

### 添加新事件类型

1. 在 `agent.types.ts` 中添加事件类型
2. 在 `agent.api.ts` 的 `dispatchEvent` 中处理
3. 在 `useAgentChat.ts` 中添加回调处理
4. 在 `AgentMessage.vue` 中展示

### 自定义工具

1. 在 `AgentToolConfig.vue` 的 `commonTools` 中添加
2. 后端需要注册对应的 MCP 工具

### 扩展消息类型

修改 `AgentMessage` 接口和 `AgentMessage.vue` 组件

## 📚 相关文档

- [后端 API 文档](../../jeecg-boot/jeecg-module-aiagent/FRONTEND_API_DOCUMENTATION.md)
- [Agent 架构文档](../../jeecg-boot/jeecg-module-aiagent/AI_AGENT_ARCHITECTURE.md)
- [MCP 工具文档](../../jeecg-boot/jeecg-module-mcp/QUICKSTART.md)

## 🎉 更新日志

### v1.0.0 (2025-11-30)
- ✨ 初始版本发布
- ✅ 支持智能任务分类
- ✅ 支持动态模型选择
- ✅ 支持知识库检索
- ✅ 支持工具调用
- ✅ 支持流式响应
- ✅ 支持多会话管理

---

**版本**: 1.0.0  
**创建时间**: 2025-11-30  
**维护者**: JeecG Team

