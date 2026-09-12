# AI 恋爱大师 + YuManus 自主规划智能体

![CI](https://github.com/jiaweiyuan633-debug/ai-love/actions/workflows/ci.yml/badge.svg)

基于 **Spring AI + RAG + MCP + Agent** 的超级智能体实战项目 —— 一个真实可用的 AI 恋爱顾问与情感陪伴产品。

## 产品功能

| 功能 | 说明 |
|---|---|
| 🔐 用户系统 | 注册/登录（JWT + BCrypt），数据按用户隔离；无数据库部署自动进入"体验模式" |
| 🎭 角色化人设 | **6 位角色 × 双模式**：恋爱顾问（风格化建议）与暖心陪伴（角色扮演聊天）随时切换；角色卡片/试听/开场白，会话绑定角色，朗读音色自动跟随 |
| 🌸 AI 朋友圈 | 角色每天以自己的口吻发一条动态（结合你的记忆，"TA 也活着"）；可点赞、评论，TA 会用角色语气回复你 |
| 🌤️ 主动关怀 | 超过半天没聊 / 早晨首次来访 / 深夜在线时，角色会"主动"发来一条贴心消息 |
| 📈 心情打卡 | 每天记录心情（5 档 emoji），打卡后角色立刻回应；7 天心情趋势折线 + 连续打卡天数 |
| 🏆 成就徽章 | 12 枚成就实时计算（对话量/打卡连续/情侣纪念日/记忆积累/多角色体验），徽章墙进度可视化 |
| 🌿 健康使用 | 连续使用 2 小时休息提醒、全站"AI 生成内容"标识、陪伴模式情感边界声明（对齐 2026《人工智能拟人化互动服务管理暂行办法》） |
| 💬 多会话对话 | 会话列表/新建/重命名/删除，历史消息 PostgreSQL 持久化，重启不丢；消息带时间戳与头像 |
| 🔍 搜索与导出 | 全文检索历史对话（关键词+摘要跳转）；一键导出会话为 Markdown |
| 🧠 长期记忆 | 自动提炼"值得记住的信息"（名字/喜好/纪念日…），后续对话注入；可查看/删除/关闭 |
| 💕 情侣绑定 | 绑定码双向绑定，共享彼此的长期记忆，恋爱纪念日"第 N 天"统计与聊天页贴心提醒 |
| 💡 追问建议 | 每轮回答结束自动生成 3 个你可能想接着问的问题，点击即发送 |
| 💌 每日情话 | 每天一句 AI 原创情话（按日缓存），进入即见，可"换一句" |
| 🔄 多轮长对话 | 40 条上下文窗口 + 重启自动从库中恢复上下文；可选 qwen-plus/turbo/max |
| 📚 RAG 知识库 | 上传 PDF/Word/MD 自动向量化，"知识库增强"开关按需检索 |
| 🎙️ 语音输入 | 连续听写（Web Speech API），识别实时上屏，说完自动断句 |
| 🔊 语音朗读 | **6 种高拟真角色音色**（CosyVoice 云端合成+缓存），边生成边朗读，浏览器免费引擎兜底 |
| 🫧 悬浮窗助手 | 可拖动悬浮球，快捷：新对话/朋友圈/朗读开关/设置，位置记忆 |
| 🎨 主题系统 | 浅色/深色/跟随系统 + 5 套主题色，全站 CSS 变量驱动 |
| 📱 PWA | 可安装到手机桌面（manifest + Service Worker 离线外壳 + 自研生成的应用图标） |
| 🤖 YuManus | ReAct 自主规划智能体：Plan → Act → Observe 全过程可视化（富 Markdown 渲染） |
| 🛠️ Tool Calling + MCP | 天气查询、PDF 恋爱报告、图片搜索/花语（MCP 服务） |

## 项目组成

| 模块 | 说明 |
|---|---|
| `backend/` | Spring Boot 3.5 + Spring AI 主应用：AI 恋爱大师（SSE 流式对话 / Tool Calling / RAG 知识库 / MCP 客户端 / 用户系统 / 会话持久化 / 长期记忆）+ YuManus ReAct 自主规划智能体 |
| `mcp-server/` | 独立 MCP 服务（Spring AI MCP Server，SSE 传输）：恋爱图片搜索、约会地点推荐等工具 |
| `frontend/` | Vue 3 + Vite + TypeScript 独立前端：登录页、会话侧边栏、聊天页（SSE 逐字渲染+语音）、设置中心、悬浮球、知识库管理、YuManus 执行过程可视化 |
| `docker/` | 本地一键联调（pgvector + 后端） |
| `docs/` | 架构设计、接口文档、部署手册 |

## 技术栈

- **Spring AI 1.1.x**（DashScope 通义千问）
- **用户系统**：JWT (jjwt) + BCrypt，Servlet 过滤器鉴权
- **持久化**：PostgreSQL（JdbcClient）——用户/会话/消息/长期记忆 + **混合 ChatMemory 仓储**（重启恢复上下文）
- **RAG 知识库**：Tika 文档解析 + TokenTextSplitter + PgVector
- **Tool Calling**：自定义工具（天气查询、PDF 报告生成等）
- **MCP 服务开发**：MCP Server (SSE) + MCP Client
- **ReAct Agent**：Plan → Act → Observe 循环
- **SSE** 流式输出（fetch 流式客户端，支持鉴权与中断）
- **前端**：Vue 3 + Vue Router + Web Speech API（TTS），CSS 变量主题系统
- **Serverless 部署**：阿里云函数计算 FC 3.0 + RDS PostgreSQL (pgvector)

## 快速开始

```bash
# 1. 启动 pgvector 向量库（宿主机端口 15432，避让本机原生 PostgreSQL）
cd docker && docker compose up -d

# 2. 配置环境变量
export DASHSCOPE_API_KEY=你的阿里云百炼APIKey
# 生产环境务必设置 JWT_SECRET（>=32 字节随机串）

# 3. 启动 MCP 服务（8102）
cd mcp-server && mvn spring-boot:run

# 4. 启动后端（8101）
cd backend && mvn spring-boot:run

# 5. 启动前端（5173）
cd frontend && npm install && npm run dev
```

打开 http://localhost:5173 → 注册账号 → 开始对话。
数据表（users/conversations/messages/user_memories/vector_store）首次启动自动创建。

> 若 MCP 服务未启动，先用 `MCP_CLIENT_ENABLED=false mvn spring-boot:run` 启动后端。
> 无数据库运行：`PERSISTENCE_ENABLED=false`（跳过登录，对话仅存内存）。
> 接口详见 [docs/API.md](docs/API.md)，云端部署详见 [docs/DEPLOY.md](docs/DEPLOY.md)。

## 上线就绪度

产品已完成"上线冲刺"加固（详见 [docs/LAUNCH.md](docs/LAUNCH.md)）：

| 类别 | 内容 |
|---|---|
| 🛡️ 安全 | 接口限流（登录防爆破 10 次/分/IP，AI 端点防刷 30 次/分/用户）、密码强度校验、JWT + BCrypt、全局异常处理（不暴露堆栈） |
| 🏥 运维 | `/actuator/health` 健康检查、logback 规范日志、数据源/JWT 全部环境变量化、Dockerfile/FC 配置对齐 |
| ⚖️ 合规 | 用户服务协议 + 隐私政策页、AI 生成内容标识、连续使用 2 小时提醒、陪伴模式情感边界声明 |
| ✨ 体验 | SSE 错误帧与断流自动重试、401 统一回登录页、PWA 发版缓存自动失效、移动端贴底面板与全面屏适配 |
| ✅ 测试 | `mvn test`：14 例集成/单元测试（注册登录鉴权、会话生命周期、心情打卡与成就、限流器），需本地 pgvector（建 `ai_love_test` 库 + vector 扩展） |

## 全流程

需求分析 → 架构设计 → 开发实现 → 产品化升级（用户系统/持久化/记忆/语音/主题）→ 竞品吸收优化（角色化/朋友圈/心情/成就）→ 上线冲刺加固（安全/合规/测试）→ 部署上线（详见 `docs/`）
