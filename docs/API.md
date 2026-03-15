# API 接口文档

> 完整接口文档可通过 Swagger UI 在线查看：`http://localhost:8080/swagger-ui.html`

## 接口响应格式

后端统一返回 `Result<T>` 结构：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {},
  "errorCode": "0"
}
```

| 字段 | 说明 |
|------|------|
| `success` | 是否成功 |
| `message` | 提示信息 |
| `data` | 业务数据 |
| `errorCode` | 响应码（成功为 `0`） |

## 错误处理说明

- 统一返回 `HTTP 200` + `success=false` + `errorCode`，前端以 `success` 与 `errorCode` 判断业务错误
- 参数校验异常由全局异常处理器统一返回 `ERR-VALIDATION`

## 响应码

| code | 含义 |
|------|------|
| 0 | 成功 |
| 1000 | 通用错误 |
| 1001 | 参数校验失败 |
| 1004 | 资源不存在 |
| 1500 | 内部错误 |

---

## 一、Agent 执行接口

### 1.1 执行 Agent（SSE 流式）

```
POST /aiagent/execute
Content-Type: application/json
Accept: text/event-stream
```

**请求体**

```json
{
  "content": "帮我查询当前时间",
  "conversationId": "conv-uuid",
  "agentId": "agent-uuid",
  "mode": "AUTO",
  "modelId": "gpt-4o-mini",
  "personalMcpTools": [
    {
      "serverId": "mcp-server-uuid",
      "toolName": "search_notion",
      "description": "搜索 Notion 文档",
      "inputSchema": {
        "type": "object",
        "properties": {
          "query": { "type": "string", "description": "搜索关键词" }
        },
        "required": ["query"]
      }
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | string | 是 | 用户输入内容 |
| `conversationId` | string | 是 | 会话 ID |
| `agentId` | string | 是 | Agent ID |
| `mode` | string | 否 | 执行模式：`AUTO`（默认）/ `MANUAL` |
| `modelId` | string | 否 | 指定模型，不传则使用 Agent 配置的默认模型 |
| `personalMcpTools` | array | 否 | PERSONAL MCP 工具 Schema 列表（前端 prefetch 后上传） |

**SSE 事件格式**

```
data: {"type":"agent:thinking","data":{"statusText":"正在思考..."}}

data: {"type":"agent:tool_call","data":{"toolName":"list_files","args":{"path":"/"},"needConfirm":false}}

data: {"type":"agent:personal_tool_call","data":{"callId":"call-uuid","toolName":"search_notion","serverId":"mcp-uuid","arguments":{"query":"会议记录"}}}

data: {"type":"agent:message","data":{"content":"当前时间"}}

data: {"type":"agent:complete","data":{"tokens":256,"durationMs":1200,"toolRounds":2}}
```

| 事件类型 | 说明 | 关键字段 |
|---------|------|---------|
| `agent:start` | 任务开始 | requestId, conversationId |
| `agent:thinking` | 推理中 | statusText |
| `agent:rag_retrieve` | RAG 检索中 | query, documentCount |
| `agent:tool_call` | GLOBAL 工具调用 | toolName, args, needConfirm, toolExecutionId |
| `agent:personal_tool_call` | PERSONAL 工具下发浏览器 | callId, toolName, serverId, arguments |
| `agent:tool_result` | 工具执行结果 | result, success, durationMs |
| `agent:message` | 流式 Token | content（增量） |
| `agent:complete` | 任务完成 | tokens, durationMs, toolRounds |
| `agent:error` | 异常 | error, message |

### 1.2 PERSONAL 工具执行结果回传

```
POST /aiagent/mcp/client-tool-result
Content-Type: application/json
```

```json
{
  "callId": "call-uuid",
  "result": "{\"items\":[{\"title\":\"会议记录\",\"url\":\"...\"}]}"
}
```

前端执行 PERSONAL MCP 工具完成后调用此接口，将结果回传给后端推理引擎继续执行。

### 1.3 工具执行确认（MANUAL 模式）

```
POST /aiagent/mcp/tool-confirm
Content-Type: application/json
```

```json
{
  "toolExecutionId": "exec-uuid",
  "action": "APPROVE"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `toolExecutionId` | string | 来自 `agent:tool_call` 事件的 toolExecutionId |
| `action` | string | `APPROVE`（批准）/ `REJECT`（拒绝） |

---

## 二、Agent 定义管理接口

### 2.1 创建 Agent

```
POST /aiagent/agent-definition
Content-Type: application/json
```

```json
{
  "name": "数据分析助手",
  "description": "专注于数据查询和分析的 AI 助手",
  "systemPrompt": "你是一名数据分析专家，擅长 SQL 查询和数据可视化...",
  "tools": {
    "serverMcpIds": ["mcp-server-001"],
    "personalMcpCapabilities": ["notion"],
    "systemTools": ["system_load_skill", "system_resolve_tools"],
    "knowledgeIds": ["kb-001"]
  },
  "contextConfig": {
    "historyMessageLoadLimit": 20,
    "maxToolRounds": 8
  },
  "ragConfig": {
    "maxResults": 5,
    "minScore": 0.7
  },
  "skillTree": [
    {
      "id": "node-001",
      "label": "SQL 技能",
      "enabled": true,
      "children": [
        {
          "id": "node-002",
          "label": "查询规范",
          "enabled": true,
          "skillId": "skill-uuid-001"
        }
      ]
    }
  ]
}
```

**响应**

```json
{
  "success": true,
  "data": {
    "id": "agent-uuid",
    "name": "数据分析助手",
    "createdAt": "2026-03-15T10:00:00"
  }
}
```

### 2.2 更新 Agent

```
PUT /aiagent/agent-definition/{agentId}
Content-Type: application/json
```

请求体与创建相同（不含 id 字段）。

### 2.3 获取 Agent 详情

```
GET /aiagent/agent-definition/{agentId}
```

**响应**（返回完整 AgentDefinition）

```json
{
  "success": true,
  "data": {
    "id": "agent-uuid",
    "name": "数据分析助手",
    "description": "...",
    "systemPrompt": "...",
    "tools": {
      "serverMcpIds": ["mcp-server-001"],
      "personalMcpCapabilities": ["notion"],
      "systemTools": ["system_load_skill"],
      "knowledgeIds": ["kb-001"]
    },
    "contextConfig": { "historyMessageLoadLimit": 20, "maxToolRounds": 8 },
    "ragConfig": { "maxResults": 5, "minScore": 0.7 },
    "skillTree": [...]
  }
}
```

### 2.4 获取 Agent 列表

```
GET /aiagent/agent-definition/list?page=1&size=20
```

**响应**

```json
{
  "success": true,
  "data": {
    "total": 5,
    "list": [
      { "id": "agent-uuid", "name": "数据分析助手", "description": "..." }
    ]
  }
}
```

### 2.5 删除 Agent

```
DELETE /aiagent/agent-definition/{agentId}
```

---

## 三、Skill 管理接口

### 3.1 创建 Skill

```
POST /aiagent/skill
Content-Type: application/json
```

```json
{
  "name": "数据库查询规范",
  "summary": "查询时必须使用参数化 SQL，禁止字符串拼接，避免 SELECT *",
  "content": "# 数据库查询规范\n\n## 基本原则\n1. 必须使用参数化查询...\n\n## 示例\n...",
  "tags": ["SQL", "数据库", "安全"]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | Skill 名称 |
| `summary` | string | 是 | 摘要（一行描述），注入 System Prompt，建议 50 字以内 |
| `content` | string | 是 | 全文内容（Markdown 格式），LLM 按需加载 |
| `tags` | array | 否 | 标签列表，用于配置页筛选 |

### 3.2 更新 Skill

```
PUT /aiagent/skill/{skillId}
Content-Type: application/json
```

### 3.3 获取 Skill 详情

```
GET /aiagent/skill/{skillId}
```

**响应**

```json
{
  "success": true,
  "data": {
    "id": "skill-uuid",
    "name": "数据库查询规范",
    "summary": "查询时必须使用参数化 SQL...",
    "content": "# 数据库查询规范\n\n...",
    "tags": ["SQL", "数据库"],
    "status": "active",
    "createTime": "2026-03-01T10:00:00"
  }
}
```

### 3.4 获取 Skill 列表

```
GET /aiagent/skill/list?page=1&size=20&tag=SQL&keyword=查询
```

| 参数 | 说明 |
|------|------|
| `tag` | 按标签筛选 |
| `keyword` | 按名称关键词搜索 |

### 3.5 删除 Skill

```
DELETE /aiagent/skill/{skillId}
```

---

## 四、MCP 服务器管理接口

### 4.1 创建 MCP 服务器

```
POST /aiagent/mcp/server
Content-Type: application/json
```

**GLOBAL MCP 示例（streamable-http）**

```json
{
  "name": "文件系统工具",
  "description": "提供文件读写、目录管理等能力",
  "scope": 0,
  "connectionType": "streamable-http",
  "endpointUrl": "http://localhost:8088/mcp",
  "authHeader": "{\"Authorization\":\"Bearer sk-xxx\"}",
  "timeoutMs": 30000,
  "readTimeoutMs": 60000,
  "enabled": 1
}
```

**GLOBAL MCP 示例（stdio 本地进程）**

```json
{
  "name": "本地搜索工具",
  "scope": 0,
  "connectionType": "stdio",
  "endpointUrl": "npx -y @modelcontextprotocol/server-filesystem /tmp",
  "enabled": 1
}
```

**PERSONAL MCP 示例（用户私有）**

```json
{
  "name": "Notion 工具",
  "description": "访问用户的 Notion 工作区",
  "scope": 1,
  "capability": "notion",
  "connectionType": "streamable-http",
  "endpointUrl": "https://notion-mcp.example.com/mcp",
  "authHeader": "{\"Authorization\":\"\"}",
  "enabled": 1
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `scope` | int | `0`=GLOBAL（服务端执行）/ `1`=PERSONAL（客户端执行） |
| `capability` | string | PERSONAL 类型专用，能力标签（如 `notion`、`github`），Agent 绑定时通过此字段匹配 |
| `connectionType` | string | `streamable-http` / `stdio` / `websocket` / `sse` |
| `endpointUrl` | string | 远端 URL 或 stdio 命令 |
| `authHeader` | string | JSON 格式认证 Header；PERSONAL 类型值为空，由浏览器运行时补充 |
| `timeoutMs` | int | 连接超时（毫秒） |
| `readTimeoutMs` | int | 读取超时（毫秒） |
| `enabled` | int | `1`=启用 / `0`=禁用 |

### 4.2 更新 MCP 服务器

```
PUT /aiagent/mcp/server/{serverId}
```

### 4.3 获取 MCP 服务器列表

```
GET /aiagent/mcp/server/list?scope=0
```

| 参数 | 说明 |
|------|------|
| `scope` | 可选，`0`=GLOBAL / `1`=PERSONAL / 不传=全部 |

**响应**

```json
{
  "success": true,
  "data": [
    {
      "id": "mcp-server-001",
      "name": "文件系统工具",
      "scope": 0,
      "connectionType": "streamable-http",
      "endpointUrl": "http://localhost:8088/mcp",
      "enabled": 1,
      "toolCount": 12
    }
  ]
}
```

### 4.4 获取 MCP 服务器工具列表

```
GET /aiagent/mcp/server/{serverId}/tools
```

**响应**

```json
{
  "success": true,
  "data": [
    {
      "name": "list_files",
      "description": "列出指定目录下的文件",
      "serverId": "mcp-server-001"
    },
    {
      "name": "read_file",
      "description": "读取文件内容"
    }
  ]
}
```

### 4.5 启用/禁用 MCP 服务器

```
PATCH /aiagent/mcp/server/{serverId}/toggle
Content-Type: application/json
```

```json
{ "enabled": 0 }
```

### 4.6 删除 MCP 服务器

```
DELETE /aiagent/mcp/server/{serverId}
```

---

## 五、会话管理接口

### 5.1 创建会话

```
POST /aiagent/conversation
Content-Type: application/json
```

```json
{
  "agentId": "agent-uuid",
  "title": "数据分析任务 - 2026-03-15"
}
```

### 5.2 获取会话列表

```
GET /aiagent/conversation/list?agentId=agent-uuid&page=1&size=20
```

### 5.3 获取会话消息历史

```
GET /aiagent/conversation/{conversationId}/messages?page=1&size=50
```

### 5.4 删除会话

```
DELETE /aiagent/conversation/{conversationId}
```

---

## 六、知识库接口

### 6.1 创建知识库

```
POST /aiagent/knowledge-base
Content-Type: application/json
```

```json
{
  "name": "产品文档",
  "description": "产品使用手册和 FAQ"
}
```

### 6.2 上传文档

```
POST /aiagent/knowledge-base/{kbId}/document
Content-Type: multipart/form-data
```

| 参数 | 说明 |
|------|------|
| `file` | 文件（支持 PDF / Word / TXT / Markdown） |
| `name` | 文档名称（可选，默认使用文件名） |

### 6.3 获取知识库列表

```
GET /aiagent/knowledge-base/list
```

### 6.4 获取知识库文档列表

```
GET /aiagent/knowledge-base/{kbId}/documents?page=1&size=20
```

### 6.5 删除文档

```
DELETE /aiagent/knowledge-base/{kbId}/document/{docId}
```

### 6.6 重建文档向量

```
POST /aiagent/knowledge-base/{kbId}/document/{docId}/rebuild
```

---

## 七、模型管理接口

### 7.1 获取可用模型列表

```
GET /aiagent/model/list
```

**响应**

```json
{
  "success": true,
  "data": [
    {
      "id": "gpt-4o-mini",
      "provider": "OPENAI",
      "type": "CHAT",
      "isDefault": true
    },
    {
      "id": "text-embedding-3-small",
      "provider": "OPENAI",
      "type": "EMBEDDING"
    }
  ]
}
```

---

## 八、系统接口

### 8.1 健康检查

```
GET /aiagent/health
```

**响应**

```json
{
  "success": true,
  "data": {
    "status": "UP",
    "redis": "UP",
    "mysql": "UP"
  }
}
```
