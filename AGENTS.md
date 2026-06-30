# AGENTS.md

Guidance for ZCode agents working in this repository.

## Project purpose

`SopAnalysisAgent` is a Spring Boot 3.3.5 + Spring AI backend that analyzes SOP
(Standard Operating Procedure) documents. It combines an LLM (DeepSeek via the
OpenAI-compatible API) with a retrieval stack: PostgreSQL + pgvector for vector
storage and external embedding/rerank services. The codebase is in an early
scaffold stage — most feature packages have not been created yet.

## Tech stack

- **Java 21**, **Spring Boot 3.3.5**, **Spring AI 1.0.3** (`spring-ai-starter-model-openai`).
- **spring-boot-starter-web** + **spring-boot-starter-webflux** (the latter is for SSE streaming responses — keep both on the classpath).
- **MyBatis-Plus 3.5.7** for persistence (mapper XML expected at `classpath:/mapper/*.xml`).
- **PostgreSQL** via the `postgresql` driver + **pgvector** (`com.pgvector:pgvector`).
- **Lombok** (configured as an annotation processor in `pom.xml`; excluded from the repackaged jar).
- **fastjson 2.0.40** for JSON.

## Build & run

This is a Maven project with the wrapper scripts `./mvnw` (Unix) / `mvnw.cmd` (Windows). The `.mvn/wrapper/maven-wrapper.jar` is git-ignored but the wrapper script still works via `distributionType=only-script`. Maven 3.9.16 is the target version.

- Build/compile: `./mvnw clean compile`
- Package: `./mvnw clean package`
- Run app: `./mvnw spring-boot:run`
- Run tests: `./mvnw test` (note: there is **no `src/test` directory yet** — no tests exist).

App listens on **port 9001**.

## Source layout

```
src/main/java/org/example/sopanalysisagent/   # base package — all app code goes here
  SopAnalysisAgentApplication.java            # @SpringBootApplication entry point
src/main/resources/
  application.yaml                            # all configuration lives here (no profiles yet)
```

New code should be placed under `org.example.sopanalysisagent.*` (controllers, service, config, mapper, etc. as sub-packages). MyBatis mapper XML files belong in `src/main/resources/mapper/`.

## Configuration & external services

`application.yaml` wires up everything. Key bindings agents must respect when editing:

- **LLM**: `spring.ai.openai.*` — currently points at DeepSeek (`https://api.deepseek.com`, model `deepseek-v4-pro`, `temperature: 0.2`). A commented-out local Ollama (`localhost:11434`) block exists as an alternative.
- **Embedding service**: `embedding.*` → `http://localhost:8082`, model `bge-reranker-base`.
- **Rerank service**: `rerank.*` → `http://localhost:8083`, model `bge-reranker-base`.
- **Database**: `spring.datasource.*` → local Postgres `agent_db`, schema `agent`, user `agent_user`. The connection URL pins `search_path=agent,public`.
- **Logging**: Spring AI packages are set to `DEBUG`.

These local endpoints (5432, 8082, 8083) must be running for the app to function — they are not mocked.

## ⚠️ Gotchas

1. **Secret in version control.** A live DeepSeek API key is hardcoded in `src/main/resources/application.yaml` (`spring.ai.openai.api-key`). The committed state has `sk-XXXX` but the working tree contains a real key. Do **not** commit the real key, and prefer externalizing it to an env var / `application-local.yaml` before any edits to this file. Flag this if the user asks to commit.
2. **No test infrastructure.** There is no `src/test` tree. If you add tests, you'll also be creating the directory structure.
3. **Both web and webflux starters are present.** This is intentional for SSE streaming; do not remove one assuming it's a mistake.
4. **`HELP.md` is Spring Initializr boilerplate**, not real project documentation.
