# AGENTS.md

Guidance for Codex agents working in this repository.

## Project purpose

`SopAnalysisAgent` is a Spring Boot 3.3.5 + **AgentScope 2.0 + A2A** backend that answers questions about SOP
(Standard Operating Procedure) documents. It combines an LLM (DashScope / Aliyun via AgentScope) with a retrieval
stack: PostgreSQL (work orders) and an external Python RAG service for embedding, vector search, and rerank.
The codebase implements a **rewrite → retrieve → answer** conversational pipeline via `PreRetrieveMiddleware`,
with ReAct tool-calling (`SearchKnowledgeTool`, `CreateWorkOrderTool`).

**Primary integration surface is A2A**, not REST chat. Other projects discover and call this agent via Agent Card.

## Tech stack

- **Java 21**, **Spring Boot 3.3.5**.
- **AgentScope 2.0.0** — `agentscope-a2a-spring-boot-starter`, `agentscope-dashscope-spring-boot-starter`,
  `agentscope-harness`, `agentscope-extensions-model-dashscope`. **Spring AI has been removed.**
- **spring-boot-starter-web** + **spring-boot-starter-webflux** (WebFlux kept for legacy SSE `/chat/stream` and
  reactive RAG client).
- **MyBatis-Plus 3.5.7** for persistence — primarily **work orders** (mapper XML at `classpath:/mapper/*.xml`).
  `chat_message` / `chat_session` mappers remain on the classpath but are **not used on the business path**.
- **PostgreSQL** via the `postgresql` driver + **pgvector** (`com.pgvector:pgvector`).
- **Lombok** (annotation processor in `pom.xml`; excluded from the repackaged jar).
- **fastjson 2.0.40** for JSON.
- **WebClient (Reactor Netty)** for HTTP calls to RAG / MES / ERP external services.

## Build & run

This is a Maven project with the wrapper scripts `./mvnw` (Unix) / `mvnw.cmd` (Windows). The `.mvn/wrapper/maven-wrapper.jar` is git-ignored but the wrapper script still works via `distributionType=only-script`. Maven 3.9.16 is the target version.

- Build/compile: `./mvnw clean compile`
- Package: `./mvnw clean package`
- Run app: `./mvnw spring-boot:run`
- Run tests: `./mvnw test`

App listens on **port 9002**.

Set `DASHSCOPE_API_KEY` (or `agentscope.dashscope.api-key` in `application.yaml`) before running.

## A2A entry point (primary)

After startup, verify Agent Card:

```bash
curl -s http://localhost:9002/.well-known/agent-card.json
```

Callers should use this URL for discovery. A2A transport (JSON-RPC / streaming) is auto-configured by
`agentscope-a2a-spring-boot-starter` from the exposed `ReActAgent` bean (`SopAgentConfig.sopReActAgent`).

### Calling from another project

```java
import io.agentscope.a2a.client.A2aAgent;
import io.agentscope.a2a.client.WellKnownAgentCardResolver;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;

import java.util.Map;

A2aAgent remote = A2aAgent.builder()
        .name("sop-analysis-agent")
        .agentCardResolver(new WellKnownAgentCardResolver(
                "http://localhost:9002",
                "/.well-known/agent-card.json",
                Map.of()))
        .build();

Msg result = remote.call(new UserMessage("开机前检查什么？")).block();
```

Pass **contextId** (maps to `sessionId`) and optional **userId** in A2A metadata so
`AgentStateStore` can restore multi-turn history for `(userId, sessionId)`.

## Source layout

```
src/main/java/org/example/sopanalysisagent/
  SopAnalysisAgentApplication.java          # @SpringBootApplication entry point
  client/
    PythonRagClient.java                     # HTTP client for the Python RAG aggregation service
    ErpClient.java / StubErpClient.java      # ERP system client (stub for now)
    MesClient.java / StubMesClient.java      # MES system client (stub for now)
  common/
    Constants.java                           # Global constants (roles, stage names, default topK)
    Result.java                              # Unified API response wrapper
  config/
    AgentScopeProperties.java                # @ConfigurationProperties("sop.agent")
    DashScopeModelConfig.java                # Fallback DashScope Model bean
    SopAgentConfig.java                      # HarnessAgent + ReActAgent for A2A
    CorsConfig.java                          # CORS configuration
    MybatisPlusConfig.java                   # MyBatis-Plus pagination plugin
    WebClientConfig.java                     # WebClient beans for RAG / MES / ERP
  controller/
    ChatController.java                      # Legacy HTTP; /chat* deprecated except /upload
  middleware/
    PreRetrieveMiddleware.java               # rewrite → RAG → inject system prompt
  mapper/
    ChatMessageMapper.java                   # Legacy; not used on business path
    ChatSessionMapper.java                   # Legacy; not used on business path
    WorkOrderMapper.java                     # Work order mapper (exists; not wired in tool)
  model/
    dto/
      RagResult.java                         # RAG search result item
      RagSearchReq.java / RagSearchResp.java # RAG search request / response
      WorkOrderReq.java                      # Work order creation request
    entity/
      ChatMessage.java / ChatSession.java    # Legacy entities
      WorkOrder.java                         # Work order entity
    vo/
      ChatStreamChunk.java                   # Legacy SSE chunk VO
  service/
    ChatSessionService.java                  # Legacy session service (unused on business path)
    QueryRewriteService.java                 # Query rewriting via AgentScope Model
    SopChatFacade.java                       # Legacy HTTP adapter over HarnessAgent
  tool/
    CreateWorkOrderTool.java                 # ReAct tool: create work order via MES stub
    SearchKnowledgeTool.java                 # ReAct tool: search knowledge base
  util/
    JsonUtils.java                           # JSON utilities (fastjson wrapper)
    MsgTexts.java                            # Extract plain text from AgentScope Msg
    PromptLoader.java                        # Classpath prompt text loader with caching
    RagContextFormatter.java                 # Format RAG hits for system prompt injection
src/main/resources/
  application.yaml                           # All configuration (no profiles yet)
  prompt/
    sop-system.txt                           # System prompt for SOP assistant
    rewrite.txt                              # Prompt for query rewriting
  mapper/
    ChatMessageMapper.xml
    ChatSessionMapper.xml
    WorkOrderMapper.xml
src/test/java/org/example/sopanalysisagent/
  service/QueryRewriteServiceTest.java
  util/MsgTextsTest.java
  util/RagContextFormatterTest.java
```

New code should be placed under `org.example.sopanalysisagent.*`. MyBatis mapper XML files belong in
`src/main/resources/mapper/`. Prompt templates belong in `src/main/resources/prompt/`.

Removed packages (do not reintroduce): `agent/`, `skill/`, `workflow/`, `ChatClientConfig`.

## Configuration & external services

`application.yaml` wires up everything. Key bindings agents must respect when editing:

- **AgentScope / A2A**: `agentscope.dashscope.api-key`, `agentscope.agent.name`, `agentscope.a2a.server.*`
  (Agent Card name/description, server enabled).
- **SOP agent**: `sop.agent.model`, `sop.agent.temperature`, `sop.agent.workspace` (Harness workspace on disk).
- **Python RAG service**: `rag.base-url` → `http://localhost:8000`, `rag.top-k: 5`. Sole retrieval entry point;
  embedding and rerank run inside the Python service.
- **Database**: `spring.datasource.*` → local Postgres `agent_db`, schema `agent`, user `agent_user`.
  Connection URL pins `search_path=agent,public`. `WorkOrderMapper` / `work_order` table exist for future
  persistence, but **CreateWorkOrderTool currently only calls the MES stub** (no DB write yet).
  Not used for A2A session state.
- **MES / ERP**: `mes.base-url` / `erp.base-url` → `http://localhost:0` (placeholder, configure when integrating).
- **Logging**: `org.example.sopanalysisagent` at `DEBUG`; `io.agentscope` at `INFO`; MyBatis-Plus SQL via `StdOutImpl`.

These local endpoints must be running for full functionality:

- **PostgreSQL**: `localhost:5432`
- **Python RAG service**: `localhost:8000` (exposes `POST /search` and `POST /ingest`)

## Conversation pipeline

`SopAgentConfig` builds a `HarnessAgent` with middleware, toolkit, workspace, and memory compaction.
Legacy HTTP (`SopChatFacade`) calls `HarnessAgent` so session defaults / compaction apply.
A2A starter still injects the exposed `ReActAgent` bean (`sopReActAgent` = harness delegate).

### PreRetrieveMiddleware (every turn, before ReAct)

1. **Rewrite** — `QueryRewriteService` uses AgentScope `Model` + `prompt/rewrite.txt` to rephrase the user query.
2. **Retrieve** — `PythonRagClient.search()` → RAG `POST /search`, returning scored chunks.
3. **Inject** — `RagContextFormatter` formats hits; middleware appends them to the system prompt via
   `onSystemPrompt` (key `PreRetrieveMiddleware.CTX_RAG`).
   Rewrite + search run on `Schedulers.boundedElastic()` (non-blocking for the event loop).

### ReAct answer + tools

4. **Answer** — Agent generates a reply using history, injected RAG context, and optional tool calls:
   - `SearchKnowledgeTool` — on-demand knowledge search
   - `CreateWorkOrderTool` — create work order via **MES stub** (`MesClient`);
     `WorkOrderMapper` exists but is **not wired** into the tool (no DB write on this path)

### Session state

5. **Persistence** — **AgentScope `AgentStateStore`** (default: `JsonFileAgentStateStore` under
   `~/.agentscope/state/`) persists conversation state by `(userId, sessionId)`.
   This is **separate from** `sop.agent.workspace` (Harness workspace on disk for skills/files).
   The business path **does not write** to `chat_message` / `chat_session`.
   Legacy mappers and `ChatSessionService` remain for reference or future cleanup only.

## API endpoints

| Method | Path           | Status       | Description                                      |
|--------|----------------|--------------|--------------------------------------------------|
| —      | `/.well-known/agent-card.json` | **Primary** | A2A Agent Card discovery                         |
| POST   | `/chat`        | **Deprecated** | Sync chat; use A2A instead                    |
| GET    | `/chat/stream` | **Deprecated** | SSE streaming chat; use A2A streaming         |
| POST   | `/chat/upload` | **Active**   | Upload SOP document (multipart → RAG `/ingest`)  |

Deprecated `/chat` and `/chat/stream` accept `sessionId` (optional; auto-generated if omitted) and `query`.
They route through `SopChatFacade` → `HarnessAgent` with `RuntimeContext(userId="http-user", sessionId)`.
Errors are returned as clear messages (`Result.fail` / SSE error text + `[DONE]`), not raw 500 stacks.

For new integrations, use **A2A only**.
