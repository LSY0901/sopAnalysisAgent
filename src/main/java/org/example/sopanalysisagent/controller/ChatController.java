package org.example.sopanalysisagent.controller;

import lombok.RequiredArgsConstructor;
import org.example.sopanalysisagent.common.Result;
import org.example.sopanalysisagent.workflow.SopWorkflow;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 对话入口：提供同步与 SSE 流式两种调用方式。
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final SopWorkflow sopWorkflow;

    /**
     * 同步对话。
     *
     * @param sessionId 会话 ID，为空则新建
     */
    @PostMapping
    public Result<Map<String, String>> chat(@RequestParam(required = false) String sessionId,
                                            @RequestParam String query) {
        String sid = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
        String answer = sopWorkflow.executeSync(sid, query);
        return Result.success(Map.of("sessionId", sid, "answer", answer));
    }

    /**
     * SSE 流式对话。
     * 推荐用 GET + EventSource 接入；data 字段为答案增量文本。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestParam(required = false) String sessionId,
                                                @RequestParam String query) {
        String sid = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
        return sopWorkflow.execute(sid, query)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .id(sid)
                        .data(chunk)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .id(sid)
                        .data("[DONE]")
                        .build()))
                // 防止上游空闲断连
                .mergeWith(Flux.interval(Duration.ofSeconds(15))
                        .map(i -> ServerSentEvent.<String>builder().comment("keep-alive").build()))
                .takeUntil(sse -> "done".equals(sse.event()));
    }
}
