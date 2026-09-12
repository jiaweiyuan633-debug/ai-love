# 商用上线手册（COMMERCIAL）

> 面向"能合法收钱、经得起监管与应用市场审核"的目标。代码侧收尾已于 2026-09 完成（AI 隐式标识、支付收口、JWT fail-fast、CSP），本文档记录**需要经营者去办理的证照、采购的资源、以及资源到位后的切换步骤**。价格与周期为经验参考，以官方当期为准。

---

## 一、证照与合规矩阵（关键路径，按启动顺序）

| # | 事项 | 前置 | 周期 | 费用（约） | 办理入口 |
|---|---|---|---|---|---|
| 1 | **经营主体**（个体户/公司 + 对公账户） | — | 1-2 周 | 0~数千（代办） | 属地市场监管 |
| 2 | **域名购买 + 实名** | 无 | 1 天 | 60~80 元/年 | 阿里云万网 |
| 3 | **ICP 备案** | 主体 + 域名 + 大陆服务器（FC/杭州区即满足） | 2-3 周 | 免费 | [beian.aliyun.com](https://beian.aliyun.com) |
| 4 | **软件著作权** | 代码完成（已具备） | 官方 30-60 工作日；代办可加急 | 官方免费 / 代办数百元 | [ccopyright.com.cn](https://www.ccopyright.com.cn)（应用市场上架硬性材料，尽早提交） |
| 5 | **支付商户号**（微信支付 + 支付宝） | 主体 + 备案域名 | 1-2 周 | 免费开通，认证数百元 | [pay.weixin.qq.com](https://pay.weixin.qq.com) / [b.alipay.com](https://b.alipay.com) |
| 6 | **EDI 许可证**（经营性 ICP） | ICP 备案完成 | 1-3 个月 | 代办 3000-8000 元 | 属地省通信管理局（会员收费业务严格意义上需要，可先备案后补办） |
| 7 | **生成式 AI 登记 + 深度合成算法备案** | 已备案大模型（通义已备案，走应用登记） | 1-3 个月 | 免费 | 属地网信办；算法备案在 [beian.cac.gov.cn](https://beian.cac.gov.cn)；iOS 中国区审核会索要 |
| 8 | **AI 内容标识**（显式 + 隐式） | 代码 | ✅ 已完成 | — | 显式：登录页/聊天页/导出标注；隐式：消息元数据 `ai_generated` + SSE `ai-meta` 帧 + TTS 音频 ID3 标识（依据《人工智能生成合成内容标识办法》2025-09-01 施行） |
| 9 | 协议/隐私页补全 | 主体信息 | 半天 | 免费 | 见下文「上线前 checklist」第 1 条 |

**关键路径**：主体 → 域名/备案 →（等待期并行：软著、keystore、材料）→ 备案过审后上线自有域名 → 商户号 → 真实支付。最快约 4 周可合法收费；应用市场上架以软著+AI 备案为准，约 2-3 个月。

## 二、资源采购单（月成本粗估 ≈ 300-700 元）

| 资源 | 规格建议 | 用途 | 月成本（约） |
|---|---|---|---|
| 域名 | `.com`/`.cn` 一枚 | 替换 `*.fcapp.run` 默认域名（备案必需） | 5 元 |
| RDS PostgreSQL | 基础系列 ≥1C2G，**内核需支持 pgvector**（阿里云 RDS PG 原生支持 `CREATE EXTENSION vector`） | 账号/会话/记忆/会员/订单持久化 | 100-300 元 |
| 云 Redis | 社区版 1G | 分布式限流（多实例共享计数） | 60-100 元 |
| SMTP | QQ/163 邮箱授权码（免费）或 阿里云邮件推送（每日 200 封免费额度） | 找回密码 / 邮箱绑定验证码 | 0 元 |
| SSL 证书 | 阿里云免费版 DV（3 个月自动续签，或买付费版省事） | HTTPS | 0-200 元/年 |
| FC/ACR/流量 | 现有三函数架构不变 | 按量计费 | 几十~几百元 |

## 三、代码侧就绪开关（资源到位后改环境变量即可）

| 能力 | 开关 | 默认 | 商用值 |
|---|---|---|---|
| 持久化 | `PERSISTENCE_ENABLED` | `true`（云端当前 false=体验模式） | `true` + 数据源三件套 + **强 `JWT_SECRET`**（默认密钥会拒绝启动） |
| 分布式限流 | `RATE_LIMIT_BACKEND` | `memory` | `redis` + `spring.data.redis.*` |
| 找回密码/邮箱绑定 | `SPRING_MAIL_HOST/USERNAME/PASSWORD` | 未配=接口 503 优雅降级 | 配置后自动启用 |
| 支付 | `PAYMENT_MODE` | `mock`（模拟收银台） | **`gateway`（接真实网关）或 `off`**——非 mock 模式模拟支付端点一律 403，防止自刷 VIP |
| CORS | `APP_CORS_ALLOWED_ORIGINS` | 已含 `tauri://localhost,http://tauri.localhost` | 追加自有域名 |

真实支付接入路径（接口已预留）：
1. 实现 `PaymentGateway.verifyNotify(payload, order)`：验签 + 金额比对（`order.priceFen`，单位分）；
2. 网关异步通知打到 `POST /payment/notify`（该路径在 JWT 保护之外，安全性完全依赖验签）；
3. `PAYMENT_MODE=gateway` 部署，模拟支付端点自动关闭。

## 四、上线前 checklist

**协议与页面（备案审核会看）**
- [ ] 协议/隐私页补：公司主体全称、运营联系方式（邮箱 + 客服渠道）、ICP 备案号占位（`frontend/src/views/AgreementView.vue`；备案号同时加到前端页脚）
- [ ] 隐私政策声明 SDK/第三方清单（DashScope、支付 SDK 等）
- [ ] 应用市场审核材料：隐私政策 URL、备案号、软著证书、版权声明

**部署**
- [ ] RDS 建库 + `CREATE EXTENSION vector` + 连接串/账号密码进 FC 环境变量
- [ ] `PERSISTENCE_ENABLED=true` + 强 `JWT_SECRET` + `RATE_LIMIT_BACKEND=redis` + SMTP 三件套 + `PAYMENT_MODE`
- [ ] 自有域名解析到 FC（自定义域名绑定 + HTTPS 证书），前端 `VITE_API_BASE` 换新域名后重发桌面/APK 包
- [ ] FC/SLS 配置告警：5xx 错误率、函数错误计数、RDS/Redis 连接失败（当前仅 stdout 日志，告警必须人工在控制台配置）
- [ ] RDS 自动备份策略确认（默认 7 天），做一次恢复演练
- [ ] 压测：AI 流式并发 50/100 路径（限流阈值 30/min/用户之下验证 429 文案）

**安全**
- [ ] 主账号旧 AccessKey `LTAI5t7...inqK` 禁用并观察后删除（悬置事项）
- [ ] DASHSCOPE_API_KEY 用 RAM 子账号 Key（已建 `ailove`）
- [ ] 支付回调金额对账任务（网关侧对账单 vs membership_orders）

## 五、客户端上架补充

- **Android**：当前 Release APK 为调试签名（可侧载）。正式上架：生成 keystore（见下）→ 配置 CI secrets → 重发正式签名包 → 各市场（华为/小米/OPPO/vivo/应用宝）提交，均需软著 + 备案号 + 隐私政策 URL。
  ```bash
  # 生成 keystore（密钥自保管，绝不入库；丢失将无法更新应用）
  keytool -genkeypair -v -keystore ailove.keystore -alias ailove \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=AI Love Master, O=<你的主体名>, C=CN"
  # 上传 base64 到 GitHub secrets：ANDROID_KEYSTORE / ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS
  ```
  CI 模板已备好（见 `.github/workflows/release.yml` 的 Android job 注释）。
- **iOS**：Apple 开发者账号（$99/年）+ Mac 构建 + 审核（需 ICP 备案号 + AI 相关证明）。
- **Windows**：建议购买代码签名证书（数百-2000 元/年），否则 SmartScreen 会拦截未签名安装包。
- **桌面自动更新**（待办）：需生成 Tauri 更新密钥对并启用 updater 插件 + `createUpdaterArtifacts`，待正式运营后配置。

## 六、执行时间线（建议）

| 时间 | 你（经营者） | 我（代码侧，已就绪/随叫随到） |
|---|---|---|
| 第 0 周 | 启动主体注册；买域名 + 提交 ICP 备案；禁用旧 AccessKey | ✅ 合规/安全代码收尾完成；协议页补占位（备案号待发下来填） |
| 第 1-2 周（备案等待期） | 提交软著申请；微信/支付宝商户号预审 | Android 正式签名流水线联调 |
| 第 2-3 周（备案过审） | 采购 RDS/Redis/SMTP/SSL | 切持久化模式部署（backend 镜像 + s.yaml 模板）、域名绑定、验证、压测 |
| 第 3-4 周 | 商户号审核通过 | 真实支付网关开发联调（约 1 周）→ `PAYMENT_MODE=gateway` 上线收费 |
| 第 1-2 月（并行） | EDI、生成式 AI 登记/算法备案 | 应用市场送审材料打包、iOS/Windows 渠道 |
