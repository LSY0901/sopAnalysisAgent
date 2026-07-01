package org.example.sopanalysisagent.controller;

import lombok.RequiredArgsConstructor;
import org.example.sopanalysisagent.client.PythonRagClient;
import org.example.sopanalysisagent.common.Result;
import org.example.sopanalysisagent.workflow.SopWorkflow;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private final PythonRagClient pythonRagClient;

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
     * 返回 Flux&lt;String&gt;，Spring WebFlux 自动按 text/event-stream 格式输出 data: 行。
     * 阻塞操作在 elastic 线程池中执行，避免阻塞 Tomcat NIO 线程。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam(required = false) String sessionId,
                                @RequestParam String query) {
        String sid = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
        return Mono.fromCallable(() -> sopWorkflow.execute(sid, query))
                .flatMapMany(Flux::from)
                .concatWithValues("[DONE]");
    }

    /**
     * 上传 SOP 文档文件，转发给 Python 服务的 /ingest 接口解析入库。
     *
     * @param file multipart 文件，字段名 file
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> upload(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("文件不能为空");
        }
        String resp = pythonRagClient.ingest(file);
        return Result.success(Map.of(
                "fileName", file.getOriginalFilename(),
                "size", file.getSize(),
                "ingest", resp == null ? "" : resp
        ));
    }
}
