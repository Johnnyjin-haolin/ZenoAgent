# 更新日志

所有重要的项目变更都会记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [2.0.0] - 2026-03

### 新增

#### 自定义 Agent 管理
- **AgentDefinition 配置体系**: 支持为每个 Agent 独立配置系统提示词、工具集、知识库、Skill 目录树和上下文行为
- **三层优先级覆盖**: 前端请求参数 → Agent 持久化配置 → 硬编码默认值
- **Agent CRUD 接口**: 新增 `AgentDefinitionController`，支持 Agent 的增删改查和列表分页
- **运行时配置隔离**: `AgentRuntimeConfig` 聚合每次请求的全部配置，与 `AgentDefinition` 解耦

#### GLOBAL / PERSONAL 双模式 MCP
- **McpScope 作用域**: 引入 `GLOBAL`（scope=0，服务端执行）和 `PERSONAL`（scope=1，客户端执行）两种 MCP 类型
- **PERSONAL MCP 密钥隔离**: PERSONAL MCP 的认证密钥只存于浏览器 localStorage，后端数据库只存 Header 名，满足数据合规要求
- **前端 prefetch 机制**: 前端在发送消息前 prefetch PERSONAL MCP 工具 Schema，随 `AgentRequest` 上传 `PersonalMcpToolSchema` 列表
- **SSE PERSONAL_TOOL_CALL 事件**: LLM 决策后通过 SSE 下发 `personal_tool_call` 事件，浏览器执行后 POST 回传结果
- **ClientToolCallManager**: 基于 `CompletableFuture` 实现 PERSONAL 工具结果等待与回传
- **McpServerController**: 新增 MCP 服务器配置的增删改查接口，支持启用/禁用
- **MCP 连接协议扩展**: 支持 `streamable-http`（远端推荐）、`stdio`（本地进程）、`websocket`、`sse` 四种传输协议
- **McpTransportFactory**: 工厂类统一管理传输协议实例化
- **McpClientFactory 懒加载**: MCP 客户端按需创建并复用，支持配置热重载（hot-reload）

#### Skill 机制
- **AgentSkill 领域对象**: 每条 Skill 包含「摘要」（注入 System Prompt）和「全文」（按需加载）
- **SkillTreeNode 树形目录**: Skill 以树形结构绑定 Agent，支持目录节点、叶节点、父子联动勾选
- **渐进式知识注入**: System Prompt 只注入 Skill 摘要，LLM 调用 `system_load_skill` 按需获取全文
- **LoadSkillTool**: 新增系统内置工具 `system_load_skill`，支持按 skillId 动态加载 Skill 全文
- **AgentSkillController**: 新增 Skill 管理接口，支持 Skill 的增删改查和标签搜索
- **批量查询优化**: 构建 System Prompt 时批量查询所有 Skill，避免 N+1 问题

#### 工具渐进式加载（Progressive Tool Loading）
- **ResolveToolsTool**: 新增系统内置工具 `system_resolve_tools`，LLM 调用后触发工具 Schema 动态加载
- **自动触发逻辑**: `ToolRegistry.isProgressiveMode()` 根据工具数量和阈值自动判断是否启用渐进式模式
- **动态追加 ToolSpecification**: 每轮推理后检测 `context.activeMcpToolNames`，追加对应完整工具定义
- **可配置阈值**: `aiagent.tools.progressive-threshold` 配置项控制触发阈值

#### MANUAL 模式 / Human-in-the-loop
- **AgentMode 枚举**: 新增 `MANUAL` 模式，工具执行前等待用户确认
- **ToolConfirmationManager**: 基于 Redisson `RBlockingQueue` 实现工具确认等待，支持跨实例分布式部署
- **超时保护**: 可配置确认超时（`MANUAL_CONFIRM_TIMEOUT_MS`），超时自动返回 `TIMEOUT` 决策
- **拒绝重规划**: 用户拒绝执行时，LLM 收到拒绝消息后可重新规划任务

### 改进

- **FunctionCallingEngine 重构**: 原 ReAct 引擎（ThinkingEngine + ObservationEngine + ActionExecutor）重构为基于 Function Calling 的 `FunctionCallingEngine`，更贴近现代 LLM API 使用方式，减少额外的 LLM 调用开销
- **LangChain4j 升级**: 从 1.9.1 升级至 1.11.0，获得更好的 MCP 客户端支持（`StreamableHttpMcpTransport`）
- **ToolRegistry 统一路由**: 新增 `ToolRegistry` 作为工具统一注册和执行入口，解耦 GLOBAL / PERSONAL / 系统工具的路由逻辑
- **AgentContext 扩展**: 新增 `activeMcpToolNames`、`executionProcess`、`todos` 等字段，支持渐进式加载和过程记录
- **SSE 事件体系完善**: 新增 `personal_tool_call`、`tool_execution_id`（MANUAL 确认关联）等事件字段

---

## [1.1.0] - 2025-12

### 新增
- 添加 Docker 支持（Dockerfile 和 docker-compose.yml）
- 添加部署文档（DEPLOYMENT.md）
- 添加贡献指南（CONTRIBUTING.md）
- 添加配置变量参考文档（docs/CONFIG_REFERENCE.md）
- 添加后端服务配置指南（BACKEND_CONFIG.md）
- 添加启动脚本（docker-start.sh、start-frontend.sh）
- 动作支持并行执行
- 全面国际化（i18n），支持中英双语切换

### 改进
- 优化项目结构，便于开源化
- 完善文档组织结构
- Nginx SSE 配置说明（proxy_buffering off / proxy_read_timeout 300s）

---

## [1.0.0] - 2024-12

### 新增
- 初始版本发布
- 基础框架搭建（Spring Boot 后端 + Vue 3 前端）
- ReAct 推理引擎（ThinkingEngine + ObservationEngine + ActionExecutor）
- RAG 知识检索（PostgreSQL + pgvector 向量存储）
- MCP 工具调用框架（GLOBAL 服务端执行）
- Agent Chat 流式对话（SSE）
- Redis 记忆管理系统（短期记忆 + MySQL 长期持久化）
- 多会话管理
- 知识库文档管理（PDF / Word / TXT / Markdown 上传与解析）
- 向量构建与相似度检索
