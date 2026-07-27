package org.example.sopanalysisagent.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final SopChatFacade sopChatFacade;
    private final PythonRagClient pythonRagClient;

    /**
     * 同步对话。
     *
     * @param sessionId 会话 ID，为空则新建
     * @param query     用户问题
     * @return sessionId 与 answer；失败时返回 Result.fail 明确错误信息
     * @deprecated 请改用 A2A（/.well-known/agent-card.json）
     */
    @Deprecated
    @PostMapping
    public Result<Map<String, String>> chat(
            @RequestParam(required = false) String sessionId,
            @RequestParam String query) {
        String sid = blankToUuid(sessionId);
        try {
            String answer = sopChatFacade.chatSync(sid, query);
            return Result.success(Map.of("sessionId", sid, "answer", answer));
        } catch (Exception e) {
            log.error("chatSync failed sessionId={}", sid, e);
            return Result.fail(clearMessage(e));
        }
    }

    /**
     * SSE 流式对话。
     *
     * @param sessionId 会话 ID，为空则新建
     * @param query     用户问题
     * @return 文本增量事件，出错时先下发错误文本，末尾仍为 [DONE]
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
                .onErrorResume(e -> {
                    log.error("chatStream failed sessionId={}", sid, e);
                    return Flux.just(clearMessage(e));
                })
                .concatWithValues("[DONE]");
    }

    /**
     * 上传 SOP 文档文件，转发给 Python 服务的 /ingest 接口解析入库。
     *
     * @param file multipart 文件，字段名 file
     * @return 上传结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> upload(
            @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("文件不能为空");
        }
        try {
            String resp = pythonRagClient.ingest(file);
            return Result.success(Map.of(
                    "fileName", file.getOriginalFilename(),
                    "size", file.getSize(),
                    "ingest", resp == null ? "" : resp
            ));
        } catch (Exception e) {
            log.error("upload failed file={}",
                    file.getOriginalFilename(), e);
            return Result.fail(clearMessage(e));
        }
    }

    /**
     * sessionId 为空时生成 UUID。
     *
     * @param sessionId 原始会话 ID
     * @return 非空会话 ID
     */
    private String blankToUuid(String sessionId) {
        return (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString() : sessionId;
    }

    /**
     * 提取可读错误信息，避免空 message。
     *
     * @param e 异常
     * @return 错误文案
     */
    private String clearMessage(Throwable e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return "服务调用失败：" + e.getClass().getSimpleName();
        }
        return msg;
    }
}
