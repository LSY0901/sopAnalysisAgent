package org.example.sopanalysisagent.config;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import lombok.RequiredArgsConstructor;
import org.example.sopanalysisagent.common.Constants;
import org.example.sopanalysisagent.middleware.PreRetrieveMiddleware;
import org.example.sopanalysisagent.tool.CreateWorkOrderTool;
import org.example.sopanalysisagent.tool.SearchKnowledgeTool;
import org.example.sopanalysisagent.util.PromptLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.util.List;

/**
 * 装配 SOP HarnessAgent，供 A2A Server 与旧 HTTP 共用。
 * <p>
 * A2A starter 通过 {@code ObjectProvider<ReActAgent>} 识别 Agent；
 * HarnessAgent 本身不继承 ReActAgent，故额外暴露其 delegate。
 */
@Configuration
@RequiredArgsConstructor
public class SopAgentConfig {

    private final PromptLoader promptLoader;
    private final PreRetrieveMiddleware preRetrieveMiddleware;
    private final SearchKnowledgeTool searchKnowledgeTool;
    private final CreateWorkOrderTool createWorkOrderTool;

    /**
     * 构建带预检索中间件与工具的 HarnessAgent。
     *
     * @return HarnessAgent 单例（含 workspace / compaction）
     */
    @Bean
    public HarnessAgent sopHarnessAgent(OpenAIChatModel model) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(searchKnowledgeTool);
        toolkit.registerTool(createWorkOrderTool);

        String sysPrompt = promptLoader.loadClasspath(Constants.PROMPT_SOP_SYSTEM);
        GenerateOptions options = GenerateOptions.builder()
                .temperature(0.2)
                .build();

        return HarnessAgent.builder()
                .name("sop-analysis-agent")
                .sysPrompt(sysPrompt)
                .model(model)
                .generateOptions(options)
                .toolkit(toolkit)
                .middlewares(List.of(preRetrieveMiddleware))
                // 最多 15 轮
                .maxIters(15)
                .workspace(Paths.get(".agentscope/workspace"))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(30)
                        .keepMessages(10)
                        .build())
                .build();
    }

}
