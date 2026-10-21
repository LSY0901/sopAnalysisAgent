package org.example.sopanalysisagent.service;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.harness.agent.HarnessAgent;
import org.example.sopanalysisagent.middleware.PreRetrieveMiddleware;
import org.example.sopanalysisagent.model.dto.McpConsultResult;
import org.example.sopanalysisagent.model.dto.RagResult;
import org.example.sopanalysisagent.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SopMcpService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SopMcpServiceTest {

    @Mock
    private HarnessAgent harnessAgent;

    private SopMcpService sopMcpService;

    @BeforeEach
    void setUp() {
        sopMcpService = new SopMcpService(harnessAgent);
    }

    @Test
    void consultSuccessWithWorkOrderAndReferences() {
        String query = "注塑机压力过高怎么报修？";
        String sessionId = "custom-session-123";

        Msg msg = Msg.builder()
                .content(List.of(TextBlock.builder()
                        .text("按照规程排查后已为您报修。工单已创建，工单号：WO-STUB-1729000000")
                        .build()))
                .build();

        when(harnessAgent.call(eq(query), any(RuntimeContext.class)))
                .thenAnswer(invocation -> {
                    RuntimeContext ctx = invocation.getArgument(1);
                    RagResult r1 = new RagResult();
                    r1.setSource("注塑机操作手册.pdf");
                    r1.setScore(0.92);
                    ctx.put(PreRetrieveMiddleware.CTX_RAG_HITS, List.of(r1));
                    return Mono.just(msg);
                });

        String json = sopMcpService.consult(Map.of("query", query, "session_id", sessionId)).block();
        assertNotNull(json);

        McpConsultResult result = JsonUtils.parse(json, McpConsultResult.class);
        assertEquals(sessionId, result.getSessionId());
        assertTrue(result.getAnswer().contains("WO-STUB-1729000000"));
        assertEquals("WO-STUB-1729000000", result.getWorkOrderNo());
        assertEquals(1, result.getReferences().size());
        assertEquals("注塑机操作手册.pdf", result.getReferences().get(0).getSource());
        assertEquals(0.92, result.getReferences().get(0).getScore());
    }

    @Test
    void consultGeneratesSessionIdWhenOmitted() {
        String query = "如何关机？";

        Msg msg = Msg.builder()
                .content(List.of(TextBlock.builder().text("请按红色开关关机。").build()))
                .build();

        when(harnessAgent.call(eq(query), any(RuntimeContext.class)))
                .thenReturn(Mono.just(msg));

        String json = sopMcpService.consult(Map.of("query", query)).block();
        assertNotNull(json);

        McpConsultResult result = JsonUtils.parse(json, McpConsultResult.class);
        assertNotNull(result.getSessionId());
        assertFalse(result.getSessionId().isBlank());
        assertEquals("请按红色开关关机。", result.getAnswer());
        assertNull(result.getWorkOrderNo());
        assertTrue(result.getReferences().isEmpty());
    }

    @Test
    void consultThrowsWhenQueryIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                sopMcpService.consult(Map.of("query", "   ")).block());

        assertThrows(IllegalArgumentException.class, () ->
                sopMcpService.consult(Map.of()).block());
    }
}
