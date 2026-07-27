package org.example.sopanalysisagent.service;

import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.common.Constants;
import org.example.sopanalysisagent.util.MsgTexts;
import org.example.sopanalysisagent.util.PromptLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Query 改写：口语问题 → 更适合检索的 query（AgentScope Model）。
 */
@Slf4j
@Service
public class QueryRewriteService {

    private final Model model;
    private final PromptLoader promptLoader;
    private final double temperature;

    /**
     * @param model        AgentScope 模型（通常为 DashScopeChatModel）
     * @param promptLoader prompt 加载器
     * @param temperature  采样温度，来自 sop.agent.temperature
     */
    public QueryRewriteService(
            Model model,
            PromptLoader promptLoader,
            @Value("${sop.agent.temperature:0.2}") double temperature) {
        this.model = model;
        this.promptLoader = promptLoader;
        this.temperature = temperature;
    }

    /**
     * 改写失败或结果为空时回退原始 query。
     *
     * @param query 原始用户问题
     * @return 改写后的 query
     */
    public String rewrite(String query) {
        try {
            String template = promptLoader.loadClasspath(Constants.PROMPT_REWRITE);
            String prompt = template.replace("{query}", query);
            String rewritten = callModel(prompt);
            log.debug("[rewrite] {} -> {}", query, rewritten);
            return (rewritten == null || rewritten.isBlank())
                    ? query : rewritten.trim();
        } catch (Exception e) {
            log.warn("[rewrite] 改写失败，回退原始 query: {}", query, e);
            return query;
        }
    }

    /**
     * 调用 Model.stream 并聚合文本（强制非流式，避免只拿到最后一截 delta）。
     *
     * @param prompt 完整改写 prompt
     * @return 模型输出文本
     */
    private String callModel(String prompt) {
        GenerateOptions options = GenerateOptions.builder()
                .stream(Boolean.FALSE)
                .temperature(temperature)
                .build();
        List<ChatResponse> responses = model.stream(
                        List.of(new UserMessage(prompt)),
                        null,
                        options)
                .collectList()
                .block();
        return MsgTexts.fromChatResponses(responses);
    }
}
