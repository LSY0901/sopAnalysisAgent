package org.example.sopanalysisagent.service;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.middleware.PreRetrieveMiddleware;
import org.example.sopanalysisagent.model.dto.McpConsultResult;
import org.example.sopanalysisagent.model.dto.RagResult;
import org.example.sopanalysisagent.util.JsonUtils;
import org.example.sopanalysisagent.util.MsgTexts;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MCP sop_consult 业务服务：调度 HarnessAgent 并组装结构化 JSON。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SopMcpService {

    private static final Pattern WORK_ORDER_PATTERN = Pattern.compile("WO-[A-Za-z0-9_-]+");

    private final HarnessAgent sopHarnessAgent;

    /**
     * 处理 MCP 工具 sop_consult 调用。
     *
     * @param args 工具入参 (query 必填, session_id 选填)
     * @return 结构化 JSON 响应 Mono
     */
    public Mono<String> consult(Map<String, Object> args) {
        return Mono.fromCallable(() -> executeConsult(args))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String executeConsult(Map<String, Object> args) {
        String query = args != null && args.get("query") != null
                ? String.valueOf(args.get("query")).trim()
                : "";
        if (query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }

        String sessionId = args != null && args.get("session_id") != null
                ? String.valueOf(args.get("session_id")).trim()
                : "";
        if (sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        log.info("[mcp-consult] start sessionId={} query={}", sessionId, query);

        RuntimeContext ctx = RuntimeContext.builder()
                .userId("mcp-user")
                .sessionId(sessionId)
                .build();

        Msg msg = sopHarnessAgent.call(query, ctx).block();
        String answer = MsgTexts.fromMsg(msg);
        if (answer == null) {
            answer = "";
        }

        String workOrderNo = extractWorkOrderNo(answer);
        List<McpConsultResult.RagReference> references = extractReferences(ctx);

        McpConsultResult result = McpConsultResult.builder()
                .answer(answer)
                .sessionId(sessionId)
                .references(references)
                .workOrderNo(workOrderNo)
                .build();

        log.info("[mcp-consult] done sessionId={} referencesCount={} hasWorkOrder={}",
                sessionId, references.size(), workOrderNo != null);

        return JsonUtils.toJson(result);
    }

    private String extractWorkOrderNo(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = WORK_ORDER_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<McpConsultResult.RagReference> extractReferences(RuntimeContext ctx) {
        if (ctx == null) {
            return Collections.emptyList();
        }
        Object hitsObj = ctx.get(PreRetrieveMiddleware.CTX_RAG_HITS);
        if (!(hitsObj instanceof List<?> rawList)) {
            return Collections.emptyList();
        }
        return rawList.stream()
                .filter(item -> item instanceof RagResult)
                .map(item -> (RagResult) item)
                .map(hit -> McpConsultResult.RagReference.builder()
                        .source(hit.getSource())
                        .score(hit.getScore())
                        .build())
                .collect(Collectors.toList());
    }
}
