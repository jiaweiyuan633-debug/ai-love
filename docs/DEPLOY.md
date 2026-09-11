# 部署手册 — 阿里云函数计算 FC 3.0

架构：前端（nginx 容器函数）→ 后端（Spring Boot 容器函数）→ RDS PostgreSQL（pgvector）；模型走通义千问（DashScope）。

> 前置条件：阿里云账号、已开通[函数计算 FC](https://fcnext.console.aliyun.com)、[百炼 DashScope](https://bailian.console.aliyun.com)（拿 API Key）、[RDS PostgreSQL](https://www.aliyun.com/product/rds/pgproxy)、[容器镜像服务 ACR](https://www.aliyun.com/product/acr)。

## 0. 数据库准备（RDS PostgreSQL + pgvector）

1. 购买 RDS PostgreSQL 16+（Serverless 实例即可），与函数计算选择**同地域**（如 cn-hangzhou）。
2. 在 RDS 控制台创建数据库 `ai_love`、账号 `ai_love`。
3. 连接数据库执行：`CREATE EXTENSION IF NOT EXISTS vector;`（表结构由应用 `initialize-schema: true` 自动创建）。
4. 白名单：添加函数计算所在 VPC 网段，或临时用 `0.0.0.0/0` 验证。

## 1. 构建并推送镜像（ACR）

```bash
# 命名空间在 ACR 控制台创建，下面替换 your-namespace
docker build -t registry.cn-hangzhou.aliyuncs.com/your-namespace/ai-love-backend:latest ./backend
docker build -t registry.cn-hangzhou.aliyuncs.com/your-namespace/ai-love-frontend:latest ./frontend
docker login registry.cn-hangzhou.aliyuncs.com
docker push registry.cn-hangzhou.aliyuncs.com/your-namespace/ai-love-backend:latest
docker push registry.cn-hangzhou.aliyuncs.com/your-namespace/ai-love-frontend:latest
```

注意：前端镜像已改为多阶段构建（容器内 npm build + nginx），`nginx.default.conf.template` 通过环境变量 `BACKEND_UPSTREAM` 注入后端地址，部署时在 FC 环境变量里把它设为后端函数的 HTTP 触发器 URL（如 `https://ai-love-backend.xxx.fcapp.run`）。

## 2. Serverless Devs 部署

```bash
npm i -g @serverless-devs/s
s config add          # 选择 Alibaba Cloud，填 AccessKeyID/Secret
export DASHSCOPE_API_KEY=sk-xxx
export SPRING_DATASOURCE_URL="jdbc:postgresql://rds内网地址:5432/ai_love"
export SPRING_DATASOURCE_USERNAME=ai_love
export SPRING_DATASOURCE_PASSWORD=xxx
s deploy
```

`s.yaml` 已定义 `ai-love-backend`（2C4G，端口 9000）与 `ai-love-frontend`（nginx，端口 80）两个 HTTP 函数。部署完成后控制台会给出两个 HTTP 触发器 URL。

## 3. 环境变量与安全

- `DASHSCOPE_API_KEY`、数据库账号密码建议改用 FC 的**密钥管理**或 KMS，不要写进代码仓。
- 生产环境将 HTTP 触发器 `authType` 改为 `signature`，或前置 API 网关做鉴权限流。
- SSE 注意事项：FC HTTP 触发器对流式响应支持良好，但网关层若有缓冲需关闭（nginx 已设 `proxy_buffering off`）。

## 4. MCP 服务（可选）

独立 MCP 服务有两种部署方式：
1. **本地运行**（开发演示）：在自己电脑 `mvn spring-boot:run`，用内网穿透（如 frp/ngrok）暴露公网，然后在 FC 后端环境变量里把 MCP 客户端指向该公网 SSE 地址（application.yml 的 `spring.ai.mcp.client.sse.connections.ai-love-mcp.url` 支持用 `MCP_SSE_URL` 环境变量覆盖，见下）。
2. **一并容器化上云**：`mcp-server/Dockerfile` 已提供，参照步骤 1-2 部署为第三个函数，后端函数将 MCP URL 指向它。

```yaml
# s.yaml 环境变量追加（配合 application.yml 的占位符）
MCP_CLIENT_ENABLED: "true"
MCP_SSE_URL: "http://ai-love-mcp.<region>.fcapp.run/sse"
```

## 5. 验证

```bash
BACKEND=https://<backend-function-url>
curl "$BACKEND/ai/chat?message=你好"
curl -N "$BACKEND/ai/love_chat/stream?message=第一次约会注意事项&chatId=demo"
curl "$BACKEND/knowledge/list"
```

打开前端函数 URL，三个页面（恋爱大师对话 / YuManus / 知识库）即可使用。

## 成本与弹性

- FC 按量付费，默认弹性缩放 0→N 实例；冷启动约 3-8s（JVM），可配置最小实例数 1 缓解。
- RDS Serverless 按用量计费；向量数据量小（<1 万分块）时成本极低。
