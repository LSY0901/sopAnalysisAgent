package org.example.sopanalysisagent.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.model.dto.RagResult;
import org.example.sopanalysisagent.service.ChatSessionService;
import org.example.sopanalysisagent.skill.AnswerSkill;
import org.example.sopanalysisagent.skill.RewriteSkill;
import org.example.sopanalysisagent.skill.RetrieveSkill;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * SOP 对话流水线：rewrite → retrieve → answer，并维护会话历史。
 * <p>
 * - rewrite / retrieve 为同步阶段（快速、确定性高）；
 * - answer 为流式阶段，结果以 Flux<String> 返回给上层 SSE 接口；
 * - 历史读取在流水线开始前，写回在回答聚合完成后。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SopWorkflow {

    private final RewriteSkill rewriteSkill;

    private final RetrieveSkill retrieveSkill;

    private final AnswerSkill answerSkill;

    private final ChatSessionService chatSessionService;

    /**
     * 执行一次完整对话，流式返回答案分片。
     */
    public Flux<String> execute(String sessionId, String query) {
        // 1. 历史
        List<String> history = chatSessionService.listHistory(sessionId);
        chatSessionService.appendUser(sessionId, query);

        // 2. 改写
        String rewritten = rewriteSkill.rewrite(query);
        log.info("[workflow] sessionId={} rewrite done", sessionId);

        // 3. 检索
        List<RagResult> context = retrieveSkill.retrieve(rewritten);
        log.info("[workflow] sessionId={} retrieve done, hits={}", sessionId, context.size());

        // 4. 生成（流式），并在流结束后把完整答案写回历史
        StringBuilder buffer = new StringBuilder();
        return answerSkill.answer(rewritten, context, history)
                .doOnNext(buffer::append)
                .doOnError(e -> log.error("[workflow] answer 失败 sessionId={}", sessionId, e))
                .doOnComplete(() -> {
                    chatSessionService.appendAssistant(sessionId, buffer.toString());
                    log.info("[workflow] sessionId={} answer done", sessionId);
                });
    }

    /**
     * 同步执行（用于非流式接口）。
     */
    public String executeSync(String sessionId, String query) {
        List<String> history = chatSessionService.listHistory(sessionId);
        chatSessionService.appendUser(sessionId, query);

        String rewritten = rewriteSkill.rewrite(query);
        List<RagResult> context = retrieveSkill.retrieve(rewritten);

        String answer = answerSkill.answerSync(rewritten, context, history);
        chatSessionService.appendAssistant(sessionId, answer);
        log.info("[workflow] sessionId={} sync answer done", sessionId);
        return answer;
    }
}
