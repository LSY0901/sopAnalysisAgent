# SopAnalysisAgent Agent Rules

Spring Boot 3.3.5 + Java 21 + AgentScope 2.0.0 (A2A) 工业 SOP 问答与工单 Agent。

本文件是跨工具 Agent 入口，只放长期有效、代码里不容易直接推断、猜错会影响结果的规则。更细的说明文档放在 `docs/`，需要时再读取。

## Tech Stack

- **Backend**: Spring Boot 3.3.5 / Java 21 / Maven 3.9+ / Lombok
- **AI & Agent**: AgentScope 2.0.0 (`agentscope-a2a-spring-boot-starter`, `ReActAgent`, `HarnessAgent`)，**已彻底移除 Spring AI**
- **Integration**: A2A (Agent-to-Agent) 协议，暴露 Agent Card 与 JSON-RPC 调用能力
- **Database & ORM**: PostgreSQL (`localhost:5432/agent_db`，schema `agent`) + MyBatis-Plus 3.5.7
- **External RAG**: 外部 Python RAG 检索微服务 (`localhost:8000`)，负责向量化（BGE-M3）与重排（BGE-Reranker）
- **HTTP Client**: WebClient (Reactor Netty)，响应式调用 RAG / MES / ERP 服务
- **Serialization**: Fastjson 2.0.40
- **Default Port**: `9003`（Agent Card: `http://localhost:9003/.well-known/agent-card.json`）

## Commands

```bash
# 编译与构建
./mvnw clean compile

# 运行测试
./mvnw test

# 本地启动（默认端口 9003）
./mvnw spring-boot:run

# 验证 A2A Agent Card 服务
curl -s http://localhost:9003/.well-known/agent-card.json
```

## Project Structure

- `src/main/java/org/example/sopanalysisagent/client/`: 外部服务 HTTP 客户端（`PythonRagClient`、MES/ERP 桩）。
- `src/main/java/org/example/sopanalysisagent/config/`: 核心配置（`SopAgentConfig` 组装 HarnessAgent 与 ReActAgent、WebClientConfig 等）。
- `src/main/java/org/example/sopanalysisagent/middleware/`: Agent 中间件（`PreRetrieveMiddleware` 实现每轮查询改写与 RAG 检索注入）。
- `src/main/java/org/example/sopanalysisagent/tool/`: ReAct 工具实现（`SearchKnowledgeTool` 知识查询、`CreateWorkOrderTool` 工单创建）。
- `src/main/java/org/example/sopanalysisagent/service/`: 核心业务编排（`QueryRewriteService` 查询改写、`SopChatFacade` 历史适配层）。
- `src/main/java/org/example/sopanalysisagent/model/`: 数据模型（`dto/` 请求响应 DTO、`entity/` 实体类、`vo/` 视图对象）。
- `src/main/resources/prompt/`: Prompt 提示词模板（`sop-system.txt`、`rewrite.txt`）。
- `src/main/resources/mapper/`: MyBatis XML 映射文件。

## Architecture

- **A2A 主驱动**：对外主入口是 **A2A 协议**而非传统 REST Chat，其他智能体或网关通过 Agent Card 发现并以 JSON-RPC 调用。
- **对话流水线（Rewrite → Retrieve → Answer）**：
  1. `PreRetrieveMiddleware` 在进入 ReAct 前拦截：
     - **Rewrite**：通过 `QueryRewriteService` 消除代词和上下文依赖，产出独立 Query。
     - **Retrieve**：调用 Python RAG 服务 `POST /search`（超时设为 300s，兼容冷启动）。
     - **Inject**：将检索结果以格式化切片注入 System Prompt（Key: `PreRetrieveMiddleware.CTX_RAG`）。
  2. `ReActAgent` 执行决策，按需调用本地工具（`SearchKnowledgeTool` / `CreateWorkOrderTool`）。
- **会话记忆隔离**：
  - 会话多轮记忆由 AgentScope 的 `AgentStateStore`（默认 `~/.agentscope/state/` 下 JSON 文件）按 `(userId, sessionId)` 自动恢复与持久化。
  - `chat_message` / `chat_session` 数据库表属于历史遗留，**主业务链路严禁读写这两张表**。
  - 工单实体 `WorkOrder` / `WorkOrderMapper` 存在，但当前 `CreateWorkOrderTool` 仅走 MES Client Stub。

## AgentScope & Tool Rules

- Tool 方法必须清晰声明工具入参实体，严谨编写注释说明工具用途，供 ReAct 引擎准确路由。
- 外部 RAG、MES、ERP 交互统一走 `client/` 下的 WebClient 封装，禁止在 Tool 或 Service 内部自行构造 HTTP 请求。
- 异步与响应式调用必须显式调度到 `Schedulers.boundedElastic()`，禁止阻塞 Reactor EventLoop 线程。
- 消息处理统一使用 AgentScope `Msg`，提取纯文本使用 `MsgTexts` 工具类。

## Backend & Coding Rules

- 请求体与 DTO 优先使用清晰的字段校验与 Lombok 注解。
- 统一 API 响应包装为 `Result<T>`。
- MyBatis-Plus Mapper 必须配置对应 XML 命名空间，自定义复杂查询放在 `resources/mapper/*.xml`。
- 日志使用 SLF4J 占位符，`org.example.sopanalysisagent` 默认级别为 `DEBUG`。

## Config And Data

- 配置集中在 `src/main/resources/application.yaml`。
- 关键密钥（如 `agentscope.openai.api-key`、`DASHSCOPE_API_KEY`）通过环境变量注入，严禁提交到 Git。
- PostgreSQL 数据源连接固定绑定 schema：`search_path=agent,public`。
- 本地依赖两个核心服务就绪：PostgreSQL (`5432`) 与 Python RAG 微服务 (`8000`)。

## Testing

- 测试使用 JUnit 5 + Mockito + AssertJ。
- 测试类统一放在 `src/test/java/org/example/sopanalysisagent/`。
- 改动改写逻辑与工具调用后，至少运行 `./mvnw test`。

## Never Do

- 不要重新引入 Spring AI 相关依赖或 Starter（本项目已全面迁移至 AgentScope 2.0.0）。
- 不要向 `chat_message` 或 `chat_session` 数据库表写入或读取数据（状态完全由 `AgentStateStore` 接管）。
- 不要把已移除的遗留包重新加回代码库（如 `agent/`、`skill/`、`workflow/`、`ChatClientConfig`）。
- 不要在主业务流或新功能中依赖已废弃的 `/chat` 和 `/chat/stream` REST 接口，新集成全部通过 A2A 协议调用。
- 不要在 WebClient 响应式调用链路中使用 `.block()` 阻塞主线程。
- 不要在 `CreateWorkOrderTool` 中绕过 MES 客户端直接硬写数据库。
- 不要硬编码 `DASHSCOPE_API_KEY` 或外部系统 URL，统一走 `application.yaml` 属性绑定。
