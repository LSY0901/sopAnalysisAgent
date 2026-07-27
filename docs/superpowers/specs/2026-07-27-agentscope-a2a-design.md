# SopAnalysisAgent → AgentScope + A2A 设计

**日期：** 2026-07-27  
**状态：** 待实现（设计已评审）  
**范围：** 将现有 Spring AI 对话流水线全面迁移到 AgentScope 2.0，并以标准 A2A 协议对外暴露；旧 `/chat` 接口过渡保留。

---

## 1. 背景与目标

### 现状

- Spring Boot 3.3.5 + Spring AI（DashScope OpenAI 兼容）
- 流水线：`rewrite → retrieve → answer`（`SopWorkflow`）
- HTTP：`POST /chat`、`GET /chat/stream`、`POST /chat/upload`
- 会话落库：Postgres `chat_session` / `chat_message`
- RAG：外部 Python 服务 `localhost:8000`（`/search`、`/ingest`）

### 目标

1. **全面替换 Spring AI**，内部统一为 AgentScope 2.0（`HarnessAgent`）。
2. **对外主协议改为 A2A**，其他项目通过 Agent Card + A2A Client / `A2aAgent` 调用。
3. **固定 URL 发现**：`/.well-known/agent-card.json`（不做 Nacos）。
4. **会话**改用 AgentScope `AgentStateStore`（不再走业务会话表写入路径）。
5. **旧对话接口过渡保留并标记废弃**；`/chat/upload` 保留。
6. **保留 SOP 检索质量**：每次调用强制预检索（rewrite + RAG），再进入 ReAct。

### 非目标（本期不做）

- Nacos / 服务注册发现
- 将 `AgentStateStore` 换为 Redis / Postgres 分布式实现（默认本地文件 state）
- 前端改造
- 删除历史会话表数据或强制数据迁移

---

## 2. 决策记录

| 决策点 | 选择 |
|--------|------|
| 对外协议 | 标准 A2A（JSON-RPC + Agent Card） |
| 旧 HTTP 对话接口 | 过渡保留，标记废弃；upload 保留 |
| 内部框架 | 全面替换为 AgentScope（去掉 Spring AI） |
| Agent 发现 | 固定 base URL + `/.well-known/agent-card.json` |
| 会话存储 | AgentScope `AgentStateStore`（弱化现有会话表） |
| 编排形态 | HarnessAgent + 强制预检索 Middleware + Toolkit |

---

## 3. 架构

```
其他项目 (A2aAgent / A2A Client)
        │  A2A JSON-RPC + Agent Card
        ▼
┌─────────────────────────────────────────┐
│  SopAnalysisAgent (port 9002)           │
│  ├── /.well-known/agent-card.json       │
│  ├── A2A transport (JSON-RPC)           │
│  └── SopHarnessAgent                    │
│        ├── PreRetrieveMiddleware        │
│        │     rewrite → Python RAG       │
│        ├── Toolkit                      │
│        │     searchKnowledge            │
│        │     createWorkOrder            │
│        └── AgentStateStore (session)    │
│                                         │
│  [废弃] /chat, /chat/stream → 同 Agent  │
│  [保留] /chat/upload → Python /ingest   │
└─────────────────────────────────────────┘
        │                    │
        ▼                    ▼
  DashScope LLM        Python RAG :8000
```

### 运行时数据流（A2A 一轮对话）

1. 调用方拉取 `http://host:9002/.well-known/agent-card.json`。
2. 发送 A2A 消息（文本问题）；建议携带 `contextId`（映射 `sessionId`）与可选 `userId`。
3. Server 组装 `RuntimeContext(userId, sessionId)`，调用 `SopHarnessAgent`。
4. `PreRetrieveMiddleware`：rewrite → `PythonRagClient.search` → 注入本轮上下文。
5. ReAct：可再调用 `searchKnowledge` / `createWorkOrder`，生成答复。
6. `AgentStateStore` 按 `(userId, sessionId)` 自动持久化。
7. 经 A2A 流式或非流式返回结果。

### 调用方示例

```java
A2aAgent remote = A2aAgent.builder()
    .name("sop-analysis-agent")
    .agentCardResolver(new WellKnownAgentCardResolver(
        "http://host:9002", "/.well-known/agent-card.json", Map.of()))
    .build();

Msg result = remote.call(new UserMessage("开机前检查什么？")).block();
```

---

## 4. 组件设计

| 组件 | 职责 |
|------|------|
| `SopAgentConfig` | 装配 `HarnessAgent`、DashScope model、toolkit、middleware、stateStore、workspace |
| `PreRetrieveMiddleware` | 每次调用前强制：query rewrite + RAG retrieve，并把片段注入上下文 |
| `SearchKnowledgeTool` | Toolkit：按需再次检索知识库 |
| `CreateWorkOrderTool` | Toolkit：创建工单（沿用 MES/ERP stub + `work_order` 表） |
| `PythonRagClient` | 保留；对接 `/search`、`/ingest` |
| A2A Server（starter 自动配置） | 暴露 Agent Card + JSON-RPC transport |
| `ChatController`（过渡） | 旧接口适配同一 Agent；对话接口 `@Deprecated` |

### 依赖变更（Maven）

**移除**

- `spring-ai-starter-model-openai`
- `spring-ai-bom`（dependencyManagement）

**新增（AgentScope 2.0.0，坐标以 Maven Central 为准）**

- `io.agentscope:agentscope-a2a-spring-boot-starter:2.0.0`（含 A2A server 自动配置，并传递 core starter）
- `io.agentscope:agentscope-dashscope-spring-boot-starter:2.0.0`（DashScope 模型装配）
- 若编译缺类再显式补：`agentscope-extensions-model-dashscope` / `agentscope-harness`

**保留**

- `spring-boot-starter-web` / `webflux`
- PostgreSQL、MyBatis-Plus、pgvector、fastjson、Lombok
- MES/ERP WebClient 与工单持久化

版本钉在 **AgentScope 2.0.0**；若与 Spring Boot 3.3.5 发生传递依赖冲突，优先用 BOM/`dependencyManagement` 对齐，必要时再评估小幅升级 Boot（需在实现计划中显式记录）。

### 配置示意

```yaml
server:
  port: 9002

agentscope:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY:sk-XXX}
  agent:
    name: sop-analysis-agent
    model: dashscope:qwen-plus
  a2a:
    server:
      enabled: true
      card:
        name: SOP Analysis Agent
        description: SOP 问答与工单助手（rewrite → retrieve → answer）

rag:
  base-url: http://localhost:8000
  top-k: 5

mes:
  base-url: http://localhost:0
erp:
  base-url: http://localhost:0
```

删除 `spring.ai.openai.*`。系统提示词继续来自 `classpath:prompt/sop-system.txt`（或迁移为 workspace `AGENTS.md`，实现时二选一，优先少改提示词语义）。

---

## 5. 改造范围

### 新增

- AgentScope 配置与 A2A 启用
- `PreRetrieveMiddleware`（承接现有 Rewrite/Retrieve 行为）
- Toolkit 版工具（从现有 `@Tool` 迁出）
- 废弃对话接口到 Agent 的适配层

### 移除或收缩

- Spring AI：`ChatClientConfig`、基于 `ChatClient` 的 `SopAgent`
- `SopWorkflow`、`RewriteSkill`、`RetrieveSkill`、`AnswerSkill`（逻辑迁入后删除）
- 业务路径不再调用 `ChatSessionService` 写历史

### 保留但弱化

- `ChatSession` / `ChatMessage` 实体、mapper、XML：保留文件避免误删历史能力，默认不写入
- `WorkOrder` 相关代码：工具继续使用

### 不变

- 端口 9002
- Python RAG 契约（`/search`、`/ingest`）
- `/chat/upload` 行为
- Java 21 基线

---

## 6. 错误处理

| 场景 | 行为 |
|------|------|
| RAG / LLM 不可用 | A2A 任务失败状态；旧 HTTP 返回明确错误信息 |
| 建工单工具失败 | 错误作为 tool observation 回传 Agent，由模型向用户说明；仅不可恢复异常才 500 |
| 缺少 session/context | 生成新 `sessionId`；`userId` 缺省为固定默认值（如 `anonymous`） |

---

## 7. 兼容策略

- **主契约**：A2A（文档与集成示例以此为准）
- **过渡契约**：`POST /chat`、`GET /chat/stream` 仍可用，内部转调同一 Agent；代码与 `AGENTS.md` 标注废弃及移除时间窗口（实现时写明「计划下一版本移除」即可）
- **稳定契约**：`POST /chat/upload` 保持 multipart → Python `/ingest`

---

## 8. 验证计划

1. `./mvnw clean compile` 通过
2. 启动后 `GET /.well-known/agent-card.json` 返回有效 Agent Card
3. A2A Client 完成一轮 SOP 问答；日志可见 rewrite + retrieve
4. 旧 `/chat/stream` 仍能 SSE 返回（过渡）
5. `/chat/upload` 入库成功

---

## 9. 风险与缓解

| 风险 | 缓解 |
|------|------|
| AgentScope starter 与 Boot 3.3 依赖冲突 | 实现期先编译验证；冲突则锁版本或小幅升 Boot |
| 去掉会话表后无法用旧 SQL 查历史 | 接受；历史改由 AgentState / workspace session log 查看 |
| 预检索增加延迟 | 保持现有「必检索」产品行为；后续可加缓存/跳过策略（非本期） |
| A2A 与旧 SSE 语义不完全一致 | 旧接口仅过渡；调用方应迁到 A2A |

---

## 10. 成功标准

- 其他项目仅依赖 base URL 即可通过 A2A 调用本 Agent
- 本仓库不再依赖 Spring AI
- SOP 问答仍默认经过检索增强
- 旧 chat 对话接口可用但明确废弃；upload 正常
