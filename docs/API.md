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

## 1.7 会话搜索与导出（需登录）

### GET /api/conversations/search?q=关键词
全文检索当前用户的历史消息（ILIKE，新→旧，最多 30 条）：
`[{"conversationId": "...", "conversationTitle": "...", "role": "user|assistant", "snippet": "…关键词附近片段…", "createdAt": "..."}]`

### GET /api/conversations/{id}/export
导出会话为 Markdown 文件下载（`Content-Disposition: attachment; filename*=UTF-8''…`）。

## 1.8 情侣绑定（需登录）

| 接口 | 说明 |
|---|---|
| GET /api/couple | 绑定状态：`{bound, pending, code, partnerNickname, anniversaryDate, daysTogether, daysToAnniversary}` |
| POST /api/couple/code | 生成我的绑定码（幂等，返回同码） |
| POST /api/couple/bind `{code}` | 用对方绑定码完成绑定（防自绑/防重复/并发冲突） |
| PATCH /api/couple `{anniversaryDate: "YYYY-MM-DD"}` | 设置恋爱纪念日 |
| DELETE /api/couple | 解除绑定（各自记忆保留，共享注入立即停止） |

绑定后，双方对话的 system prompt 自动注入"情侣绑定信息块"：伴侣昵称、纪念日、在一起第 N 天、
纪念日当天/7 天内提醒，以及伴侣最近 10 条长期记忆（共享记忆）。

## 1.9 每日情话

### GET /api/daily-quote?refresh=false
返回 `{"date": "2026-09-12", "quote": "…"}`。每天一句，首次请求 qwen-turbo 生成并按日缓存，
`refresh=true` 重新生成并覆盖当天缓存。体验模式（无数据库）下降级为每次实时生成。

## 1.10 人设语音合成（TTS）

### GET /api/tts/voices
返回可用人设音色列表：
`[{"id":"yujie","label":"温柔御姐","emoji":"🌙","description":"低柔从容、成熟魅力"}, …]`

6 种人设（后端 CosyVoice 音色映射）：温柔御姐(yujie/loongbella)、邻家小妹(xiaomei/longwan)、
高冷总裁(ceo/longcheng)、清纯男大(nanda/longshu)、中二少年(zhonger/longjielidou)、知心姐姐(jiejie/longxiaoxia)。

### POST /api/tts
```json
{"text": "别怕，有我在呢。", "persona": "yujie", "rate": 1.0}
```
返回 `audio/mpeg` 二进制（单次 ≤300 字，前端按句合成）。rate 为用户语速（0.5-2.0，与
人设基准语速相乘后夹取）。结果按 (人设+语速+文本) 内存 LRU 缓存（200 条），重复朗读零成本零延迟。
调用链：DashScope CosyVoice 非实时接口 → 返回 24h 音频 URL → 后端下载转投（前端不直连，
避免 HTTPS 混合内容问题）。体验模式同样可用。

## 1.11 角色目录与双模式（需登录）

### GET /api/personas
返回 6 位角色的完整卡片信息（id/name/emoji/tagline/color + 两种模式的开场白）。
统一数据源：`PersonaCatalog`（聊天 system prompt、TTS 音色、朋友圈语气均引用它）。

### 会话绑定角色
- `POST /api/conversations` 请求体支持 `persona`、`mode`（`advisor`=恋爱顾问 / `companion`=暖心陪伴）；
  companion 模式自动落库一条角色开场白。
- `PATCH /api/conversations/{id}` 在会话 0 条消息时可改绑 persona/mode。
- 对话接口（/ai/love_chat/stream 等）新增 `persona`、`mode` 参数：
  advisor 用恋爱大师底稿+角色咨询风格，companion 用陪伴底稿（含情感边界约束）+角色人设。

### POST /api/conversations/{id}/suggestions
按最后一轮问答生成 3 个追问建议（qwen-turbo，进程内 LRU 缓存）：`{"suggestions": ["…","…","…"]}`

## 1.12 AI 朋友圈（需登录）

### GET /api/moments?persona=jiejie
返回该角色最近 10 条动态（含评论）。今天的动态不存在时同步生成一次（结合用户长期记忆，
角色口吻，40~80 字），按 (用户, 角色, 日期) 幂等。

### POST /api/moments/{id}/like
切换点赞，返回 `{"liked": true}`。

### POST /api/moments/{id}/comments
```json
{"content": "哈哈好呀"}
```
插入用户评论并异步生成角色回复（人设语气），返回全部评论。

## 1.13 主动关怀（需登录）

### GET /api/care/pending?persona=jiejie
触发规则：超过 12 小时没聊天 / 当天早晨(5-11 点)首次来访 / 深夜(23 点后)在线。
命中时返回 `{"type":"away|morning|night","content":"…"}`（角色口吻，按用户+日期+类型去重），
否则返回 `{}`。

## 1.14 心情打卡与成就（需登录）

### GET /api/mood
`{"checkedToday":false,"todayScore":null,"streak":0,"recent7":[{"date":"2026-09-12","score":4,"note":null}, …]}`
recent7 固定 7 天（未打卡日 score=0，旧→新）。

### POST /api/mood?persona=jiejie
```json
{"score": 4, "note": "今天很顺利"}
```
打卡（当日重复打卡覆盖），返回 `{"reply":"角色的回应","status":{…}}`。

### GET /api/achievements
12 枚成就实时计算（对话量/心情连续打卡/情侣绑定与在一起天数/记忆条数/多角色体验）：
`[{"id":"chat_10","name":"畅聊新手","emoji":"🌱","description":"累计对话 10 轮","unlocked":false,"progress":0.4,"progressText":"4/10 轮"}, …]`

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

数据表：users / conversations / messages / user_memories / couples / daily_quotes / vector_store（首次启动自动创建）。
PWA：生产构建后可通过浏览器"安装到桌面/主屏幕"（manifest + Service Worker，localhost 与 HTTPS 下可用）。

> 环境变量：`JWT_SECRET`（生产必须覆盖，HS256 密钥 ≥32 字节）、`PERSISTENCE_ENABLED`（false=体验模式，跳过登录与持久化）。
> 云端部署见 [DEPLOY.md](DEPLOY.md)；买了 RDS 后把 `PERSISTENCE_ENABLED=true` 并配好数据源即可开通全部功能。
