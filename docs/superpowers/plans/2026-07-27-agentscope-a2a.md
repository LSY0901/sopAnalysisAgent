# AgentScope + A2A Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 SopAnalysisAgent 从 Spring AI 全面迁移到 AgentScope 2.0，并以标准 A2A 对外暴露；保留强制预检索与过渡期旧 `/chat` 接口。

**Architecture:** `HarnessAgent`（或 `ReActAgent`）单例 + `PreRetrieveMiddleware`（rewrite → RAG → 注入 system prompt）+ Toolkit 工具；`agentscope-a2a-spring-boot-starter` 自动暴露 Agent Card / JSON-RPC。旧 `ChatController` 适配同一 Agent 并标记废弃。会话改用 `AgentStateStore`，不再写 `chat_message`。

**Tech Stack:** Java 21、Spring Boot 3.3.5、AgentScope 2.0.0、DashScope、A2A、WebClient RAG、MyBatis-Plus（工单）

**Spec:** `docs/superpowers/specs/2026-07-27-agentscope-a2a-design.md`

**Commit 策略:** 仅在用户明确要求时执行 git commit；计划中的 commit 步骤标为可选。

---

## File Structure

| 路径 | 职责 |
|------|------|
| `pom.xml` | 去掉 Spring AI，加入 AgentScope A2A + DashScope |
| `src/main/resources/application.yaml` | `agentscope.*` / `a2a` 配置，删除 `spring.ai` |
| `config/SopAgentConfig.java` | 装配 Model、Toolkit、Middleware、Agent Bean |
| `config/AgentScopeProperties.java` | 绑定自定义配置（model 名、workspace 路径等） |
| `middleware/PreRetrieveMiddleware.java` | 强制 rewrite + retrieve，注入上下文 |
| `service/QueryRewriteService.java` | 用 AgentScope Model 做 query 改写 |
| `service/SopChatFacade.java` | 供旧 HTTP 调用同一 Agent（sync/stream） |
| `tool/SearchKnowledgeTool.java` | 改为 AgentScope `@Tool` |
| `tool/CreateWorkOrderTool.java` | 改为 AgentScope `@Tool` |
| `util/RagContextFormatter.java` | 纯函数：RAG 列表 → 文本（可单测） |
| `controller/ChatController.java` | 对接 Facade；对话接口 `@Deprecated` |
| `AGENTS.md` | 文档改为 A2A 主契约 |
| **删除** | `ChatClientConfig`、`agent/SopAgent`、`workflow/SopWorkflow`、`skill/*`、Answer 相关 Spring AI 路径 |

保留：`PythonRagClient`、`PromptLoader`、MES/ERP、WorkOrder、upload。

弱化保留（不删文件、业务不再调用）：`ChatSessionService`、session mapper。

---

### Task 1: Maven 依赖切换

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 更新 properties 与 dependencyManagement**

在 `pom.xml` 的 `<properties>` 中增加：

```xml
<agentscope.version>2.0.0</agentscope.version>
```

删除整个 `spring-ai.version` 与 `spring-ai-bom` 的 `dependencyManagement` 块。

- [ ] **Step 2: 替换依赖**

删除：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

新增：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-a2a-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-dashscope-spring-boot-starter</artifactId>
    <version>${agentscope.version}</version>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>${agentscope.version}</version>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>${agentscope.version}</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 3: 解析依赖**

Run:

```bash
./mvnw.cmd -q dependency:resolve -DincludeArtifactIds=agentscope-a2a-spring-boot-starter,agentscope-harness
```

Expected: BUILD SUCCESS。若 `dashscope-spring-boot-starter` 坐标 404，改为仅保留 `agentscope-extensions-model-dashscope` + 手动 `DashScopeChatModel` Bean（Task 4 兜底）。

---

### Task 2: 配置文件

**Files:**
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: 替换 LLM / A2A 配置**

将 `application.yaml` 改为（保留 datasource / rag / mes / erp / mybatis-plus）：

```yaml
server:
  port: 9002

spring:
  application:
    name: SopAnalysisAgent
  datasource:
    url: jdbc:postgresql://localhost:5432/agent_db?options=-c%20search_path%3Dagent%2Cpublic
    username: agent_user
    password: 123456

agentscope:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY:sk-XXX}
  agent:
    name: sop-analysis-agent
  a2a:
    server:
      enabled: true
      card:
        name: SOP Analysis Agent
        description: SOP 问答与工单助手（rewrite → retrieve → answer）

sop:
  agent:
    model: qwen-plus
    temperature: 0.2
    workspace: .agentscope/workspace

mes:
  base-url: http://localhost:0
erp:
  base-url: http://localhost:0

rag:
  base-url: http://localhost:8000
  top-k: 5

logging:
  level:
    org.example.sopanalysisagent: DEBUG
    io.agentscope: INFO

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
    mapper-locations: classpath:/mapper/*.xml
```

删除全部 `spring.ai.*`。

---

### Task 3: RAG 文本格式化（可单测）

**Files:**
- Create: `src/main/java/org/example/sopanalysisagent/util/RagContextFormatter.java`
- Create: `src/test/java/org/example/sopanalysisagent/util/RagContextFormatterTest.java`

- [ ] **Step 1: 写失败测试**

```java
package org.example.sopanalysisagent.util;

import org.example.sopanalysisagent.model.dto.RagResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextFormatterTest {

    @Test
    void emptyReturnsEmpty() {
        assertEquals("", RagContextFormatter.format(List.of()));
    }

    @Test
    void formatsSourceAndContent() {
        RagResult r = new RagResult();
        r.setSource("sop-1.pdf");
        r.setContent("先断电");
        r.setScore(0.9);
        String text = RagContextFormatter.format(List.of(r));
        assertTrue(text.contains("sop-1.pdf"));
        assertTrue(text.contains("先断电"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./mvnw.cmd -q -Dtest=RagContextFormatterTest test`  
Expected: 编译失败或测试失败（类不存在）

- [ ] **Step 3: 实现**

```java
package org.example.sopanalysisagent.util;

import org.example.sopanalysisagent.model.dto.RagResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 将 RAG 命中结果格式化为可注入 prompt 的文本。
 */
public final class RagContextFormatter {

    private RagContextFormatter() {
    }

    /**
     * 格式化检索片段；空列表返回空串。
     */
    public static String format(List<RagResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        return results.stream()
                .map(r -> "- (来源:" + r.getSource() + ") " + r.getContent())
                .collect(Collectors.joining("\n"));
    }
}
```

- [ ] **Step 4: 再跑测试**

Run: `./mvnw.cmd -q -Dtest=RagContextFormatterTest test`  
Expected: BUILD SUCCESS

---

### Task 4: AgentScope 工具迁移

**Files:**
- Modify: `src/main/java/org/example/sopanalysisagent/tool/SearchKnowledgeTool.java`
- Modify: `src/main/java/org/example/sopanalysisagent/tool/CreateWorkOrderTool.java`

- [ ] **Step 1: 改写 SearchKnowledgeTool**

去掉 Spring AI 注解，改用：

```java
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
```

方法签名保持不变；`@Tool(description = "...")`、`@ToolParam(name = "query", description = "...")`（AgentScope 要求参数带 `name`）。

完整类：

```java
package org.example.sopanalysisagent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.example.sopanalysisagent.client.PythonRagClient;
import org.example.sopanalysisagent.model.dto.RagResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 知识检索工具。供 Agent 在需要 SOP 规程时调用。
 */
@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool {

    private final PythonRagClient pythonRagClient;

    /**
     * 在企业 SOP 知识库中检索。
     */
    @Tool(name = "search_knowledge",
            description = "在企业 SOP 知识库中检索操作规程、设备手册、"
                    + "安全规范、故障排查步骤等知识。"
                    + "当需要给出具体操作步骤或规范依据时调用。")
    public String searchKnowledge(
            @ToolParam(name = "query",
                    description = "检索关键词或问题，应为清晰的设备名/操作目标/故障现象")
            String query,
            @ToolParam(name = "top_k",
                    description = "返回的条数，默认5", required = false)
            Integer topK) {
        List<RagResult> results = pythonRagClient.search(query, topK);
        if (results.isEmpty()) {
            return "未检索到相关 SOP 知识。";
        }
        return IntStream.range(0, results.size())
                .mapToObj(i -> {
                    RagResult r = results.get(i);
                    String score = r.getScore() != null
                            ? String.format("%.2f", r.getScore()) : "N/A";
                    return String.format("[%d] (来源:%s, score:%s) %s",
                            i + 1, r.getSource(), score, r.getContent());
                })
                .collect(Collectors.joining("\n"));
    }
}
```

- [ ] **Step 2: 改写 CreateWorkOrderTool**

```java
package org.example.sopanalysisagent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.example.sopanalysisagent.client.MesClient;
import org.springframework.stereotype.Component;

/**
 * 创建工单工具。供 Agent 在用户报修/派单时调用。
 */
@Component
@RequiredArgsConstructor
public class CreateWorkOrderTool {

    private final MesClient mesClient;

    /**
     * 在 MES 创建设备工单。
     */
    @Tool(name = "create_work_order",
            description = "在 MES 系统创建设备工单（报修/派工）。"
                    + "当用户要求报修、派单、创建工单时调用。返回工单号。")
    public String createWorkOrder(
            @ToolParam(name = "device_code", description = "设备编号")
            String deviceCode,
            @ToolParam(name = "description", description = "故障或需求描述")
            String description,
            @ToolParam(name = "priority",
                    description = "优先级：1-高 2-中 3-低", required = false)
            Integer priority) {
        int p = (priority == null) ? 2 : priority;
        String orderNo = mesClient.createWorkOrder(deviceCode, description, p);
        return "工单已创建，工单号：" + orderNo;
    }
}
```

---

### Task 5: Query 改写服务

**Files:**
- Create: `src/main/java/org/example/sopanalysisagent/service/QueryRewriteService.java`

- [ ] **Step 1: 用 AgentScope Model 实现改写**

```java
package org.example.sopanalysisagent.service;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatModel;
import io.agentscope.core.model.GenerateOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.common.Constants;
import org.example.sopanalysisagent.util.PromptLoader;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Query 改写：口语问题 → 更适合检索的 query。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService {

    private final ChatModel chatModel;
    private final PromptLoader promptLoader;

    /**
     * 改写失败时回退原始 query。
     */
    public String rewrite(String query) {
        try {
            String template = promptLoader.loadClasspath(Constants.PROMPT_REWRITE);
            String prompt = template.replace("{query}", query);
            Msg response = chatModel.stream(
                            List.of(new UserMessage(prompt)),
                            null,
                            GenerateOptions.builder().build())
                    .blockLast();
            String rewritten = extractText(response);
            log.debug("[rewrite] {} -> {}", query, rewritten);
            return (rewritten == null || rewritten.isBlank())
                    ? query : rewritten.trim();
        } catch (Exception e) {
            log.warn("[rewrite] 失败，回退原始 query: {}", query, e);
            return query;
        }
    }

    private String extractText(Msg msg) {
        if (msg == null) {
            return null;
        }
        // 实现时按 AgentScope Msg API 取文本内容
        // （如 msg.getTextContent() / getContent()），以编译期 API 为准
        return String.valueOf(msg);
    }
}
```

**实现注意：** `ChatModel.stream` / `Msg` 取文本方法以依赖 jar 源码为准；若 starter 提供的 Bean 类型不是 `ChatModel`，在 `SopAgentConfig` 中显式声明 `DashScopeChatModel` Bean。`extractText` 必须在 Task 6 联调时改成真实 API，禁止留下 `String.valueOf(msg)` 上线。

---

### Task 6: PreRetrieveMiddleware + Agent 装配

**Files:**
- Create: `src/main/java/org/example/sopanalysisagent/config/AgentScopeProperties.java`
- Create: `src/main/java/org/example/sopanalysisagent/middleware/PreRetrieveMiddleware.java`
- Create: `src/main/java/org/example/sopanalysisagent/config/SopAgentConfig.java`
- Delete: `src/main/java/org/example/sopanalysisagent/config/ChatClientConfig.java`

- [ ] **Step 1: Properties**

```java
package org.example.sopanalysisagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SOP Agent 本地配置（模型名、workspace 等）。
 */
@Data
@ConfigurationProperties(prefix = "sop.agent")
public class AgentScopeProperties {

    /** DashScope 模型名，如 qwen-plus */
    private String model = "qwen-plus";

    private double temperature = 0.2;

    /** Harness workspace 目录 */
    private String workspace = ".agentscope/workspace";
}
```

- [ ] **Step 2: PreRetrieveMiddleware**

在 `onAgent` 开头：从最后一条用户消息取 query → `QueryRewriteService.rewrite` → `PythonRagClient.search` → `ctx.put("rag_context", formatted)`。

在 `onSystemPrompt`：把 `rag_context` 追加到 system prompt。

```java
package org.example.sopanalysisagent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.message.Msg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.client.PythonRagClient;
import org.example.sopanalysisagent.model.dto.RagResult;
import org.example.sopanalysisagent.service.QueryRewriteService;
import org.example.sopanalysisagent.util.RagContextFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

/**
 * 强制预检索：rewrite → RAG → 注入 system prompt。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreRetrieveMiddleware implements MiddlewareBase {

    public static final String CTX_RAG = "rag_context";

    private final QueryRewriteService queryRewriteService;
    private final PythonRagClient pythonRagClient;

    @Value("${rag.top-k:5}")
    private int topK;

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        String query = extractLastUserText(input.msgs());
        String rewritten = queryRewriteService.rewrite(query);
        List<RagResult> hits = pythonRagClient.search(rewritten, topK);
        String ragText = RagContextFormatter.format(hits);
        ctx.put(CTX_RAG, ragText);
        log.info("[pre-retrieve] hits={} rewritten={}", hits.size(), rewritten);
        return next.apply(input);
    }

    @Override
    public Mono<String> onSystemPrompt(
            Agent agent, RuntimeContext ctx, String currentPrompt) {
        Object rag = ctx.get(CTX_RAG);
        if (rag == null || String.valueOf(rag).isBlank()) {
            return Mono.just(currentPrompt);
        }
        String appended = currentPrompt
                + "\n\n以下是检索到的 SOP 知识（请优先依据它作答，并标注来源）：\n"
                + rag;
        return Mono.just(appended);
    }

    private String extractLastUserText(List<Msg> msgs) {
        if (msgs == null || msgs.isEmpty()) {
            return "";
        }
        // 实现时按 Msg API 取最后一条用户文本
        Msg last = msgs.get(msgs.size() - 1);
        return String.valueOf(last);
    }
}
```

`extractLastUserText` / `onSystemPrompt` 签名若与 jar 不完全一致，以 `MiddlewareBase` 接口为准微调（v2 文档含 `RuntimeContext` 参数）。

- [ ] **Step 3: SopAgentConfig 装配 Agent Bean**

```java
package org.example.sopanalysisagent.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import lombok.RequiredArgsConstructor;
import org.example.sopanalysisagent.common.Constants;
import org.example.sopanalysisagent.middleware.PreRetrieveMiddleware;
import org.example.sopanalysisagent.tool.CreateWorkOrderTool;
import org.example.sopanalysisagent.tool.SearchKnowledgeTool;
import org.example.sopanalysisagent.util.PromptLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.util.List;

/**
 * 装配 SOP HarnessAgent，供 A2A Server 与旧 HTTP 共用。
 */
@Configuration
@EnableConfigurationProperties(AgentScopeProperties.class)
@RequiredArgsConstructor
public class SopAgentConfig {

    private final AgentScopeProperties properties;
    private final PromptLoader promptLoader;
    private final PreRetrieveMiddleware preRetrieveMiddleware;
    private final SearchKnowledgeTool searchKnowledgeTool;
    private final CreateWorkOrderTool createWorkOrderTool;

    /**
     * A2A starter 识别 ReActAgent（HarnessAgent 为其子类）。
     */
    @Bean
    public ReActAgent sopReActAgent() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(searchKnowledgeTool);
        toolkit.registerTool(createWorkOrderTool);

        String sysPrompt = promptLoader.loadClasspath(Constants.PROMPT_SOP_SYSTEM);
        String modelId = "dashscope:" + properties.getModel();

        return HarnessAgent.builder()
                .name("sop-analysis-agent")
                .sysPrompt(sysPrompt)
                .model(modelId)
                .toolkit(toolkit)
                .middlewares(List.of(preRetrieveMiddleware))
                .workspace(Paths.get(properties.getWorkspace()))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }
}
```

若 `HarnessAgent.builder()` 不接受 `.middlewares` / `.toolkit`，改用 `ReActAgent.builder()` 完成同等装配（仍满足 A2A），workspace/compaction 可后补。

删除 `ChatClientConfig.java`。

- [ ] **Step 4: 编译**

Run: `./mvnw.cmd -q clean compile`  
Expected: BUILD SUCCESS。按编译错误修正 import / 方法签名（Msg 文本提取、Middleware 钩子签名、Model Bean）。

---

### Task 7: 旧 HTTP 适配层

**Files:**
- Create: `src/main/java/org/example/sopanalysisagent/service/SopChatFacade.java`
- Modify: `src/main/java/org/example/sopanalysisagent/controller/ChatController.java`
- Delete: `workflow/SopWorkflow.java`、`skill/*.java`、`agent/SopAgent.java`

- [ ] **Step 1: SopChatFacade**

```java
package org.example.sopanalysisagent.service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 旧 HTTP 接口对 AgentScope Agent 的适配。
 */
@Service
@RequiredArgsConstructor
public class SopChatFacade {

    private final ReActAgent sopReActAgent;

    /**
     * 同步问答。
     */
    public String chatSync(String sessionId, String query) {
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("http-user")
                .sessionId(sessionId)
                .build();
        Msg msg = sopReActAgent.call(new UserMessage(query), ctx).block();
        return msg == null ? "" : extractText(msg);
    }

    /**
     * 流式问答（仅文本 delta）。
     */
    public Flux<String> chatStream(String sessionId, String query) {
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("http-user")
                .sessionId(sessionId)
                .build();
        return sopReActAgent.streamEvents(new UserMessage(query), ctx)
                .filter(e -> e.getType() == AgentEventType.TEXT_BLOCK_DELTA)
                .map(e -> ((TextBlockDeltaEvent) e).getDelta());
    }

    private String extractText(Msg msg) {
        // 与 Task 5 共用同一取文本工具方法（实现时抽到 MsgTexts 工具类）
        return String.valueOf(msg);
    }
}
```

若 `streamEvents` / `call` 签名无 `RuntimeContext` 重载，按 jar API 调整（可能是 `call(msg).contextWrite(...)`）。

- [ ] **Step 2: 更新 ChatController**

```java
package org.example.sopanalysisagent.controller;

import lombok.RequiredArgsConstructor;
import org.example.sopanalysisagent.client.PythonRagClient;
import org.example.sopanalysisagent.common.Result;
import org.example.sopanalysisagent.service.SopChatFacade;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * 对话入口（过渡期）。主契约已迁移至 A2A，本控制器计划在下一版本移除对话接口。
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final SopChatFacade sopChatFacade;
    private final PythonRagClient pythonRagClient;

    /**
     * @deprecated 请改用 A2A（/.well-known/agent-card.json）
     */
    @Deprecated
    @PostMapping
    public Result<Map<String, String>> chat(
            @RequestParam(required = false) String sessionId,
            @RequestParam String query) {
        String sid = blankToUuid(sessionId);
        String answer = sopChatFacade.chatSync(sid, query);
        return Result.success(Map.of("sessionId", sid, "answer", answer));
    }

    /**
     * @deprecated 请改用 A2A streaming
     */
    @Deprecated
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam(required = false) String sessionId,
            @RequestParam String query) {
        String sid = blankToUuid(sessionId);
        return Mono.fromCallable(() -> sopChatFacade.chatStream(sid, query))
                .flatMapMany(flux -> flux)
                .concatWithValues("[DONE]");
    }

    /**
     * 上传 SOP 文档，转发 Python /ingest。
     */
    @PostMapping(value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> upload(
            @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("文件不能为空");
        }
        String resp = pythonRagClient.ingest(file);
        return Result.success(Map.of(
                "fileName", file.getOriginalFilename(),
                "size", file.getSize(),
                "ingest", resp == null ? "" : resp));
    }

    private String blankToUuid(String sessionId) {
        return (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString() : sessionId;
    }
}
```

- [ ] **Step 3: 删除旧流水线类**

删除文件：

- `src/main/java/.../workflow/SopWorkflow.java`
- `src/main/java/.../skill/RewriteSkill.java`
- `src/main/java/.../skill/RetrieveSkill.java`
- `src/main/java/.../skill/AnswerSkill.java`
- `src/main/java/.../agent/SopAgent.java`

- [ ] **Step 4: 编译**

Run: `./mvnw.cmd -q clean compile`  
Expected: BUILD SUCCESS

---

### Task 8: 文档更新

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: 重写关键段落**

更新内容必须包含：

1. Tech stack：AgentScope 2.0 + A2A，不再写 Spring AI  
2. 主入口：A2A Agent Card `http://localhost:9002/.well-known/agent-card.json`  
3. 其他项目用 `A2aAgent` + `WellKnownAgentCardResolver` 调用示例  
4. 流水线：PreRetrieveMiddleware（rewrite→retrieve）+ ReAct tools  
5. 会话：AgentStateStore，不再依赖 chat_message 业务写入  
6. 标注 `/chat`、`/chat/stream` 废弃；`/chat/upload` 保留  
7. Source layout 同步新包（middleware、SopAgentConfig、SopChatFacade）

---

### Task 9: 端到端验证

- [ ] **Step 1: 单元测试 + 编译打包**

```bash
./mvnw.cmd -q clean test package -DskipTests=false
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 启动应用（需本机 Postgres；LLM Key 有效）**

```bash
./mvnw.cmd spring-boot:run
```

- [ ] **Step 3: 检查 Agent Card**

```bash
curl -s http://localhost:9002/.well-known/agent-card.json
```

Expected: JSON 含 `name` / `url` / skills 或 capabilities（非 404）

- [ ] **Step 4: 过渡接口冒烟（可选）**

```bash
curl -s -X POST "http://localhost:9002/chat?query=开机前检查什么"
```

Expected: `code` 成功且 `answer` 非空（依赖 RAG/LLM）

- [ ] **Step 5:（可选）用户要求时再 commit**

勿自动 push。若用户要求提交，按仓库风格写 message，例如：

```
migrate SOP agent to AgentScope 2.0 with A2A server

Replace Spring AI pipeline with HarnessAgent, pre-retrieve middleware,
and A2A exposure; keep deprecated /chat adapters and upload.
```

---

## Spec Coverage Checklist

| Spec 要求 | Task |
|-----------|------|
| 全面替换 Spring AI | 1, 6, 7 |
| A2A Server + Agent Card | 1, 2, 6 |
| 固定 URL 发现（无 Nacos） | 2 |
| AgentScope 会话 StateStore | 6（Harness 默认） |
| 强制预检索 rewrite→retrieve | 5, 6 |
| Toolkit search / work order | 4, 6 |
| 旧 chat 废弃保留 | 7 |
| upload 保留 | 7 |
| 更新 AGENTS.md | 8 |
| 验证 compile / card / 冒烟 | 9 |

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-27-agentscope-a2a.md`.

**两种执行方式：**

1. **Subagent-Driven（推荐）** — 每个 Task 开一个子代理，Task 间做审查，迭代快  
2. **Inline Execution** — 本会话按 executing-plans 逐项执行，设检查点  

选哪个？
