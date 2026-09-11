# 部署手册 — 阿里云函数计算 FC 3.0（已上线）

架构：前端（nginx 容器函数）→ 后端（Spring Boot 容器函数）→ MCP 服务（独立容器函数）；模型走通义千问（DashScope）。数据库（RDS PostgreSQL + pgvector）暂未接入，RAG 知识库功能云端关闭，其余功能全量可用。

**线上地址（杭州）**

| 服务 | 地址 |
|---|---|
| 前端 | https://ai-lovefrontend-xmplmppqhf.cn-hangzhou.fcapp.run |
| 后端 | https://ai-love-backend-bewmrbwajv.cn-hangzhou.fcapp.run |
| MCP 服务 | https://ai-lovep-server-ulqjkbqbad.cn-hangzhou.fcapp.run/sse |

## 一、镜像仓库（ACR 个人版 · 新版权限体系）

- 仓库：`crpi-ygiptetxof3iff6u.cn-hangzhou.personal.cr.aliyuncs.com/ai_love/ai_love`
- 三个镜像用 tag 区分：`backend-latest` / `frontend-latest` / `mcp-server-latest`
- 登录：`docker login --username <账号全名> crpi-....personal.cr.aliyuncs.com`（密码是 ACR 控制台「访问凭据」里设置的固定密码，独立于 AccessKey）
- ⚠️ 新版 Docker Desktop 构建的镜像默认带 OCI provenance 附件清单，ACR 个人版会拒收（`unknown manifest class for oci.empty`），构建时必须加 `--provenance=false --sbom=false`

## 二、部署

```bash
npm i -g @serverless-devs/s
s config add --AccessKeyID xxx --AccessKeySecret xxx -a default
export DASHSCOPE_API_KEY=sk-xxx
export BACKEND_UPSTREAM=https://<backend 函数 URL>     # frontend nginx 反代目标
s deploy backend -y && s deploy frontend -y && s deploy mcp -y
```

注意：`s deploy <资源>` 实际会解析整个 s.yaml，其中 `${env(...)}` 引用的环境变量必须全部已导出。函数自定义容器需显式声明 `diskSize`（与 `cpu` 成对）。

## 三、云端无数据库模式（当前状态）

后端支持按环境变量裁剪功能，当前云端配置：

| 环境变量 | 值 | 作用 |
|---|---|---|
| `KNOWLEDGE_ENABLED` | false | 关闭知识库接口与检索工具 |
| `INLINE_LOVE_TOOLS` | true | 内联图片搜索/花语工具（替代 MCP 远程调用） |
| `MCP_CLIENT_ENABLED` | false | 关闭 MCP 客户端 |
| `SPRING_AUTOCONFIGURE_EXCLUDE` | DataSource/pgvector 两个自动配置类 | 应用可无数据库启动 |

RAG 端点在关闭时返回友好提示而非报错。以后买了 RDS：设 `KNOWLEDGE_ENABLED=true`、数据源三个变量、去掉 SPRING_AUTOCONFIGURE_EXCLUDE 里的相关项，重新 `s deploy backend` 即可，再把 `data/knowledge/*.md` 重新上传入库。

## 四、踩坑实录（重要）

1. **MCP SSE 在 FC 上的状态性问题**：MCP 的 SSE 会话保存在实例内存中，FC 会把 `/sse` 长连接和后续 `/mcp/messages` POST 路由到不同实例，导致 `Session not found`，应用启动即失败。FC 支持 `sessionAffinity`（会话亲和），但依赖客户端回传 Cookie，Spring AI 的 WebClient 不保证。**当前方案**：云端 `INLINE_LOVE_TOOLS=true` 内联同样的工具；`mcp-server` 模块保留用于本地/长驻服务器环境（本地 `mvn spring-boot:run` 一切正常）。
2. **nginx 尾斜杠 301**：`location /knowledge/ {}`（带斜杠前缀 + 代理）会让不带斜杠的 `/knowledge` 请求被 nginx 自动 301 补斜杠，而 FC 默认域名禁止外部重定向（`ExternalRedirectForbidden`）→ 400。解法：加 `location = /knowledge` 精确匹配返回 SPA 页面，且 `try_files $uri /index.html`（去掉 `$uri/`）。
3. **ACR 镜像 tag 不更新**：FC 按镜像 tag 解析摘要，重新 push 同 tag 后 `s deploy` 不一定触发更新，换新 tag（如 `frontend-v2`）最稳。

## 五、安全清单

- [ ] 部署用的 AccessKey 建议之后轮换（曾出现在对话中）
- [ ] 生产将 HTTP 触发器 `authType` 改为签名鉴权或前置 API 网关
- [ ] DASHSCOPE_API_KEY 与 ACR 密码建议迁入 FC 密钥管理/KMS
- [ ] RDS 上线后，白名单从 0.0.0.0/0 收紧到 FC 的 VPC 网段
