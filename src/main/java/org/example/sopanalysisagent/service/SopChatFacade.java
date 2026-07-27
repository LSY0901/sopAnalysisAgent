package org.example.sopanalysisagent.service;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.example.sopanalysisagent.util.MsgTexts;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 旧 HTTP 接口对 AgentScope HarnessAgent 的适配。
 * <p>
 * 走 HarnessAgent 以保证 session 默认值与 memory compaction 生效；
 * A2A 仍由 starter 注入 {@code ReActAgent} delegate。
 */
@Service
@RequiredArgsConstructor
public class SopChatFacade {

    private final HarnessAgent sopHarnessAgent;

    /**
     * 同步问答。
     *
     * @param sessionId 会话 ID
     * @param query     用户问题
     * @return 助手回复文本；无结果时返回空串
     */
    public String chatSync(String sessionId, String query) {
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("http-user")
                .sessionId(sessionId)
                .build();
        Msg msg = sopHarnessAgent.call(query, ctx).block();
        String text = MsgTexts.fromMsg(msg);
        return text == null ? "" : text;
    }

    /**
     * 流式问答（仅文本 delta）。
     *
     * @param sessionId 会话 ID
     * @param query     用户问题
     * @return 文本增量流
     */
    public Flux<String> chatStream(String sessionId, String query) {
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("http-user")
                .sessionId(sessionId)
                .build();
        return sopHarnessAgent.streamEvents(query, ctx)
                .filter(e -> e.getType() == AgentEventType.TEXT_BLOCK_DELTA)
                .map(e -> ((TextBlockDeltaEvent) e).getDelta());
    }
}
