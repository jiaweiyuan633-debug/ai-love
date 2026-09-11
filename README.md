# AI 恋爱大师 + YuManus 自主规划智能体

基于 **Spring AI + RAG + MCP + Agent** 的超级智能体实战项目。

## 项目组成

| 模块 | 说明 |
|---|---|
| `backend/` | Spring Boot 3.5 + Spring AI 主应用：AI 恋爱大师（SSE 流式对话 / Tool Calling / RAG 知识库 / MCP 客户端）+ YuManus ReAct 自主规划智能体 |
| `mcp-server/` | 独立 MCP 服务（Spring AI MCP Server，SSE 传输）：恋爱图片搜索、约会地点推荐等工具 |
| `frontend/` | Vue 3 + Vite + TypeScript 独立前端：聊天页（SSE 逐字渲染）、知识库管理、YuManus 执行过程可视化 |
| `docker/` | 本地一键联调（pgvector + 后端） |
| `docs/` | 架构设计、接口文档、部署手册 |

## 技术栈

- **Spring AI 1.1.x**（DashScope 通义千问）
- **RAG 知识库**：Tika 文档解析 + TokenTextSplitter + PgVector
- **Tool Calling**：自定义工具（天气查询、PDF 报告生成等）
- **MCP 服务开发**：MCP Server (SSE) + MCP Client
- **ReAct Agent**：Plan → Act → Observe 循环
- **SSE** 流式输出
- **Serverless 部署**：阿里云函数计算 FC 3.0 + RDS PostgreSQL (pgvector)

## 快速开始

```bash
# 1. 启动 pgvector 向量库（宿主机端口 15432，避让本机原生 PostgreSQL）
cd docker && docker compose up -d

# 2. 配置环境变量
export DASHSCOPE_API_KEY=你的阿里云百炼APIKey

# 3. 启动 MCP 服务（8102）
cd mcp-server && mvn spring-boot:run

# 4. 启动后端（8101）
cd backend && mvn spring-boot:run

# 5. 启动前端（5173）
cd frontend && npm install && npm run dev
```

> 若 MCP 服务未启动，先用 `MCP_CLIENT_ENABLED=false mvn spring-boot:run` 启动后端。
> 接口详见 [docs/API.md](docs/API.md)。

## 全流程

需求分析 → 架构设计 → 开发实现 → 部署上线（详见 `docs/`）
