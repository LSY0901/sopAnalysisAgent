package org.example.sopanalysisagent.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.common.Constants;
import org.example.sopanalysisagent.util.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Query 改写技能：把口语化的用户问题改写为更适合检索的 query。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RewriteSkill {

    private final ChatClient chatClient;
    private final PromptLoader promptLoader;

    /**
     * @param query 原始用户问题
     * @return 改写后的 query；失败时回退为原始 query
     */
    public String rewrite(String query) {
        try {
            String template = promptLoader.loadClasspath(Constants.PROMPT_REWRITE);
            String prompt = template.replace("{query}", query);
            String rewritten = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            log.debug("[rewrite] {} -> {}", query, rewritten);
            return (rewritten == null || rewritten.isBlank()) ? query : rewritten.trim();
        } catch (Exception e) {
            log.warn("[rewrite] 改写失败，回退原始 query: {}", query, e);
            return query;
        }
    }
}
