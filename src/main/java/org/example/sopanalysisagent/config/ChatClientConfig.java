package org.example.sopanalysisagent.config;

import org.example.sopanalysisagent.common.Constants;
import org.example.sopanalysisagent.util.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置。
 * <p>
 * spring-ai-starter-model-openai 会自动配置 {@link ChatModel}（OpenAiChatModel）。
 * 这里基于它构造应用级单例 {@link ChatClient}，装载默认 system prompt。
 * 工具与 advisor 由 {@code SopAgent} 在构造时按需挂载（见 agent 包）。
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel, PromptLoader promptLoader) {
        String systemPrompt = promptLoader.loadClasspath(Constants.PROMPT_SOP_SYSTEM);
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build();
    }
}
