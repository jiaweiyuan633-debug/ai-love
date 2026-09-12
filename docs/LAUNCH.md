# 上线手册（Launch Checklist）

本文档回答一个问题：**从"本地能跑"到"真正上线给用户用"，还需要做哪些事。**
按两档目标划分，做完对应清单即可上线。

---

## 档位一：小范围自用 / 朋友体验（现有云函数部署即可）

当前部署形态：阿里云函数计算 FC 三个函数（backend / frontend / mcp-server），FC 默认域名自带 HTTPS，
体验模式运行（无数据库，无登录，数据不保存）。**此档位不需要买任何东西**，按下面清单核对即可。

### 上线前检查清单

- [ ] **重新构建并部署**：本地代码已更新（本冲刺四个 commit），云端还是旧镜像。
      执行 `mvn package` / `docker build` / `s deploy backend frontend`（见 [DEPLOY.md](DEPLOY.md) 第二节），
      否则手机端 PWA 与全部新功能（角色选择/朋友圈/心情打卡/成就/协议页）都不会出现。
- [ ] **`DASHSCOPE_API_KEY` 已配置**：`s.yaml` 从本机环境变量注入，确认本机 export 后再 deploy。
- [ ] **健康检查**：部署后访问 `https://<backend-fc-url>/actuator/health`，应返回 `{"status":"UP"}`。
- [ ] **限流已默认开启**：登录 10 次/分钟/IP、AI 端点 30 次/分钟/用户（体验模式按 IP）。
      如需调整：`RATE_LIMIT_AUTH_PER_MINUTE` / `RATE_LIMIT_AI_PER_MINUTE` 环境变量。
- [ ] **协议页可访问**：`https://<frontend-fc-url>/agreement` 与 `/privacy` 正常打开。
- [ ] **Key 轮换（强烈建议）**：阿里云 AccessKey 曾在开发对话中出现过，上线前去
      RAM 控制台**创建新 Key 并禁用旧 Key**。

### 本档位的已知限制

- 数据不持久化（体验模式）：刷新/重新部署后对话与记忆清空——这是"不买数据库"的直接结果，不是缺陷。
- 内存限流在 FC 多实例下各实例独立计数，防刷能力打折；防爆破仍有效（单 IP 连续尝试会被 429 挡住的概率高）。

---

## 档位二：正式对外运营（需要购买资源 + 备案）

### 必做清单

1. **购买 RDS PostgreSQL（带 pgvector 插件）或自建 pgvector 容器**
   - RDS 需要 pgvector 插件（阿里云 RDS PG 支持开启），用于长期记忆与 RAG 向量检索；
   - 配置 RDS 白名单：放通函数计算 VPC 网段；
   - 建库 `ai_love`，启用 vector 扩展。
2. **打开持久化并注入环境变量**（`s.yaml` 中 backend 资源，已留注释位）：

   ```yaml
   PERSISTENCE_ENABLED: "true"
   KNOWLEDGE_ENABLED: "true"
   SPRING_DATASOURCE_URL: "jdbc:postgresql://<RDS地址>:5432/ai_love"
   SPRING_DATASOURCE_USERNAME: "ai_love"
   SPRING_DATASOURCE_PASSWORD: <强密码>
   JWT_SECRET: <强随机串，至少32字节，如 openssl rand -base64 48 生成>
   ```

   打开登录后，**JWT_SECRET 绝不能留默认值**（`application.yml` 里的默认值只用于本地开发）。
3. **绑定自定义域名 + HTTPS**
   - FC 控制台绑定自定义域名，CNAME 解析到 FC；
   - 证书用阿里云免费 SSL（每年 20 张额度）；
   - 前端 PWA、AI 内容标识等在 HTTPS 下才完整生效。
4. **ICP 备案（法律必须）**
   - 国内服务器/函数对外提供 Web 服务，域名必须完成 ICP 备案；
   - AI 拟人化互动 + 生成式 AI 服务，规模扩大后按《人工智能拟人化互动服务管理暂行办法》
     （2026年7月生效）与《生成式人工智能服务管理暂行办法》可能需要算法备案/安全评估——
     小规模起步可先完成 ICP 备案并保留合规功能（已有：AI 内容标识、防沉迷提醒、
     情感边界声明、协议页），用户量上来后咨询当地网信部门。
5. **配置备份**：RDS 控制台开启自动备份（默认 7 天）；数据含用户情感记录，建议保留期 ≥ 30 天。
6. **监控**：FC 控制台配置函数错误率/耗时告警；`/actuator/health` 接入可用性拨测。

### AccessKey 轮换操作指引（2026-09-12 确认：当前是主账号 Key，只能在控制台轮换）

主账号 AccessKey 没有任何 API 可以创建/禁用，必须在控制台登录（密码 + 短信验证）后操作：

1. 登录 [RAM 控制台 - 身份管理](https://ram.console.aliyun.com/overview)，推荐做 **方案 A**：
   - **方案 A（推荐，权限最小化）**：左侧"用户"→ 创建用户 `ai-love-deploy`（勾选"OpenAPI 调用访问"）→ 授权
     `AliyunFCFullAccess` + `AliyunContainerRegistryFullAccess` → 为该用户创建 AccessKey；
   - **方案 B（快捷，风险同旧 Key）**：页面右上角头像 → AccessKey 管理 → 创建 AccessKey（短信验证）。
2. 新 Key 就绪后更新本地部署凭据（替换命令中的三个占位）：
   `s config add --AccessKeyID <新ID> --AccessKeySecret <新Secret> -a default --force`
3. 回到控制台**禁用并删除旧 Key**（`LTAI5t7...inqK`）：方案 A 在"用户详情 - AccessKey"里，
   方案 B 在主账号 AccessKey 管理页。建议先禁用观察 2-3 天再删除。
4. 影响面确认：ACR 镜像仓库登录密码独立于 AccessKey（docker push 不受影响）；
   DASHSCOPE_API_KEY 无关；该 Key 仅本机 `s` CLI 在用。
7. **PEXELS_API_KEY（可选）**：MCP 图片搜索工具的图源 key，不用该功能可不配。

### 上线后首批用户观察项

- DashScope 用量控制台：确认调用量与费用符合预期（限流值就是你的费用安全阀）；
- 日志：FC 日志中心按 `ERROR` 级别检索 `GlobalExceptionHandler` 输出；
- 慢查询：RDS 控制台查看 messages/memories 查询耗时（索引已建好：`idx_messages_conversation`、
  `idx_user_memories_user`、`idx_conversations_user`）。

---

## 本次"上线冲刺"改了什么（代码已就绪，无需重复劳动）

| 项 | 说明 |
|---|---|
| 生产反代修复 | nginx 模板补齐 `/api`、`/auth` 反代（原配置生产部署后登录与核心接口全挂）+ gzip |
| 接口限流 | 登录/注册按 IP 每分钟 10 次防爆破；AI 消耗端点按用户每分钟 30 次防刷（`RateLimitFilter`，内存滑动窗口，无新依赖） |
| 全局异常处理 | 统一 `{"error": "..."}` 格式；AI 上游故障转 502、内容审核拦截转 422，友好文案 |
| SSE 错误帧 | `[ERROR] ` 帧 + 前端识别，AI 报错不再永远停在"思考中" |
| 健康检查 | `/actuator/health`（actuator），可作 FC 探活 |
| 日志规范 | logback 统一格式，`LOG_LEVEL` 环境变量可调 |
| 用户协议 + 隐私政策 | `/agreement`、`/privacy`（AI 声明/数据收集/第三方/用户权利/未成年人），登录页与设置有入口 |
| 401 统一处理 | token 过期任何接口都自动回登录页 |
| SSE 断流重试 | 零内容接收的原始连接故障自动重试一次（regenerate 语义，不产生重复历史） |
| 密码强度 | 注册至少 8 位且含字母数字 |
| PWA 发版 | sw.js 版本号构建时自动注入时间戳，缓存自动失效 |
| 移动端 | 设置弹窗贴底面板、标签横滚、聊天输入栏 safe-area |
| 集成测试 | 14 例（注册登录鉴权/会话生命周期/心情成就/限流），`mvn test` 全绿；跑测试需本地 pgvector（建 `ai_love_test` 库 + vector 扩展） |

## 明确未做（后续方向）

- 前端组件测试与 CI 流水线；
- Redis 分布式限流（多实例精确计数）；
- 支付/订阅、多语言；
- 手机号注册/实名（对外运营规模化后按需）。
