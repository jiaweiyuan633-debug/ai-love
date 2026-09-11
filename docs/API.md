# AI 恋爱大师 — 接口文档

Base URL: `http://127.0.0.1:8101`（backend）

## 1. AI 对话

### GET /ai/chat
最简同步对话（无人设、无记忆），用于连通性测试。

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| message | string | 你好，介绍一下你自己 | 用户消息 |

返回：`text/plain` 完整回复。

### GET /ai/love_chat
恋爱大师同步对话（人设 + 会话记忆 + 工具调用）。

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| message | string | 必填 | 用户消息 |
| chatId | string | default | 会话 ID（隔离记忆） |

### GET /ai/love_chat/stream
恋爱大师 SSE 流式对话，`text/event-stream`，逐 token 推送。

参数同上。前端用 EventSource 接收。

### GET /ai/love_chat/rag_stream
RAG 检索增强版流式对话：先检索知识库 top4 片段再回答。参数同上。

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

1. `cd docker && docker compose up -d`（pgvector，宿主机端口 **15432**）
2. `cd mcp-server && mvn spring-boot:run`（8102）
3. `cd backend && mvn spring-boot:run`（8101，需要 `DASHSCOPE_API_KEY`；MCP 服务未启动时先 `MCP_CLIENT_ENABLED=false`）
4. `cd frontend && npm run dev`（5173，Vite 代理 /ai、/knowledge → 8101）
