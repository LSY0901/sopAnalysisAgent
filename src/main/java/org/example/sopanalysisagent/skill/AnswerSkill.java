package org.example.sopanalysisagent.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.agent.SopAgent;
import org.example.sopanalysisagent.model.dto.RagResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 生成答案技能：把检索上下文交给 Agent，返回流式回答。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerSkill {

    private final SopAgent sopAgent;

    /**
     * 流式生成答案。
     *
     * @param query    用户问题（已改写）
     * @param context  检索到的知识片段
     * @param history  对话历史
     */
    public Flux<String> answer(String query, List<RagResult> context, List<String> history) {
        log.debug("[answer] 流式生成 query={} ctx={}", query, context.size());
        return sopAgent.chat(query, context, history);
    }

    /**
     * 同步生成答案（聚合为完整字符串）。
     */
    public String answerSync(String query, List<RagResult> context, List<String> history) {
        log.debug("[answer] 同步生成 query={} ctx={}", query, context.size());
        return sopAgent.chatSync(query, context, history);
    }
}
