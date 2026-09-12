# AI 恋爱大师 — 接口文档

Base URL: `http://127.0.0.1:8101`（backend）

> **鉴权**：持久化模式（默认，需数据库）下，除 `/auth/**` 外的所有接口都需要
> `Authorization: Bearer <JWT>`（登录后获得，7 天有效）。无数据库的体验模式（`PERSISTENCE_ENABLED=false`）
> 不需要 token，对话仅存内存。

## 0. 用户系统

### GET /auth/status
探测是否启用登录/持久化：`{"enabled": true|false}`。前端据此决定显示登录页还是进入体验模式。

### POST /auth/register
```json
{"username": "xiaoyao", "password": "123456", "nickname": "阿瑶"}
```
用户名 3-32 位（字母/数字/下划线/中文），密码 6-64 位。返回 `{"token": "...", "user": {...}}`。

### POST /auth/login
```json
{"username": "xiaoyao", "password": "123456"}
```
返回同上；密码错误返回 401。

### GET /auth/me · PATCH /auth/me
获取/更新个人信息（昵称、长期记忆开关 `memoryEnabled`）。

## 1. AI 对话

### GET /ai/chat
恋爱大师同步对话（人设 + 会话记忆 + 长期记忆注入 + 工具调用）。

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| message | string | 必填 | 用户消息 |
| chatId | string | default | 会话 ID（隔离记忆，自动落库） |

### GET /ai/love_chat/stream
恋爱大师 SSE 流式对话，`text/event-stream`，逐 token 推送，`data:[DONE]` 结束。
每一轮问答结束后自动落库（messages 表），首轮结束自动生成会话标题，并异步提炼长期记忆。

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| message | string | 必填 | 用户消息 |
| chatId | string | default | 会话 ID |
| regenerate | boolean | false | true 时先删除库中最后一轮问答再重新作答 |
| model | string | qwen-plus | qwen-plus / qwen-turbo / qwen-max |

### GET /ai/love_chat/rag_stream
RAG 检索增强版流式对话：先检索知识库 top4 片段再回答。参数同上。

## 1.5 会话管理（需登录）

### GET /api/conversations
当前用户会话列表（按更新时间倒序）：
`[{"id": "...", "title": "第一次约会地点选择", "ragEnabled": false, "updatedAt": "...", "messageCount": 2}]`

### POST /api/conversations
新建会话，body 可选 `{"title": "..."}`（默认"新对话"）。

### GET /api/conversations/{id}/messages
历史消息：`[{"id": 1, "role": "user", "content": "...", "createdAt": "..."}]`

### PATCH /api/conversations/{id}
重命名：`{"title": "新标题"}`

### DELETE /api/conversations/{id}
删除会话及全部消息（同时清空模型上下文缓存）。

## 1.6 长期记忆（需登录）

### GET /api/memory
`{"enabled": true, "items": [{"id": 1, "content": "用户名字是阿瑶", "createdAt": "..."}]}`

### DELETE /api/memory/{id} · DELETE /api/memory
删除单条 / 清空全部记忆。开关在 `PATCH /auth/me` 的 `memoryEnabled`。

## 2. YuManus 智能体

### GET /ai/yumanus/stream
ReAct 自主规划执行过程 SSE。每一步推送：`── 第 N 步 ──`、模型输出（Thought/Action/Action Input）、`【观察】…`、`【最终回答】…`。

| 参数 | 类型 | 说明 |
|---|---|---|
| task | string | 任务描述 |

## 3. 知识库管理

### POST /knowledge/upload
multipart 上传文档（PDF/Word/PPT/MD/TXT，≤20MB），自动 Tika 解析 → 切分 → 向量化入库。

| 参数 | 类型 | 说明 |
|---|---|---|
| file | file | 文档文件 |

返回：`{"file_name": "...", "chunks": 12}`

### GET /knowledge/list
返回：`[{"file_name": "...", "doc_id": "uuid", "chunks": 12}, ...]`

### DELETE /knowledge/{docId}
删除指定文档的全部分块。

## 4. MCP 服务（独立进程，端口 8102）

- SSE 端点：`GET http://127.0.0.1:8102/sse`
- 消息端点：`POST /mcp/messages?sessionId=...`
- 暴露工具：`searchLoveImage`（Pexels 图片搜索）、`getFlowerMeaning`（花语查询）

## 内置工具一览（模型可调用）

| 工具 | 来源 | 说明 |
|---|---|---|
| getWeather | backend 本地 | Open-Meteo 查询城市当前天气 |
| generateLoveReport | backend 本地 | OpenPDF 生成中文恋爱报告 PDF |
| searchKnowledge | backend 本地 | RAG 知识库向量检索 |
| searchLoveImage | MCP 服务 | Pexels 浪漫图片搜索（需 PEXELS_API_KEY） |
| getFlowerMeaning | MCP 服务 | 常见花语查询 |

## 启动顺序

1. `cd docker && docker compose up -d`（pgvector，宿主机端口 **15432**；users/conversations/messages/user_memories/vector_store 表自动创建）
2. `cd mcp-server && mvn spring-boot:run`（8102）
3. `cd backend && mvn spring-boot:run`（8101，需要 `DASHSCOPE_API_KEY`；MCP 服务未启动时先 `MCP_CLIENT_ENABLED=false`）
4. `cd frontend && npm run dev`（5173，Vite 代理 /ai、/knowledge、/api、/auth → 8101）
5. 浏览器打开 http://localhost:5173 → 注册/登录 → 开始对话

> 环境变量：`JWT_SECRET`（生产必须覆盖，HS256 密钥 ≥32 字节）、`PERSISTENCE_ENABLED`（false=体验模式，跳过登录与持久化）。
> 云端部署见 [DEPLOY.md](DEPLOY.md)；买了 RDS 后把 `PERSISTENCE_ENABLED=true` 并配好数据源即可开通全部功能。
