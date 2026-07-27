package org.example.sopanalysisagent.config;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.executor.runner.AgentRequestOptions;
import io.agentscope.core.a2a.server.executor.runner.AgentRunner;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 覆盖 A2A 默认 Runner：显式传入 {@link RuntimeContext}。
 * <p>
 * 默认 {@code ReActAgentWithStarterRunner} 调用 {@code stream(List)}，中间件拿到的
 * ctx 为 null，导致预检索无法写入。此处改走 {@code streamEvents(msgs, ctx)}。
 */
@Configuration
public class SopA2aRunnerConfig {

    /**
     * 注册带 RuntimeContext 的 A2A AgentRunner（ConditionalOnMissingBean 会被覆盖）。
     *
     * @param harnessAgent SOP HarnessAgent
     * @return AgentRunner
     */
    @Bean
    public AgentRunner sopA2aAgentRunner(HarnessAgent harnessAgent) {
        return new SopA2aAgentRunner(harnessAgent);
    }

    /**
     * 使用 HarnessAgent.streamEvents + RuntimeContext 的 A2A Runner。
     */
    @RequiredArgsConstructor
    static final class SopA2aAgentRunner implements AgentRunner {

        private final HarnessAgent harnessAgent;
        private final Map<String, ReActAgent> running =
                new ConcurrentHashMap<>();

        /**
         * @return Agent 名称
         */
        @Override
        public String getAgentName() {
            return harnessAgent.getName();
        }

        /**
         * @return Agent 描述
         */
        @Override
        public String getAgentDescription() {
            String desc = harnessAgent.getDescription();
            return desc == null ? "" : desc;
        }

        /**
         * 流式执行，并将 AgentEvent 转为 A2A 所需的 Event。
         *
         * @param msgs    输入消息
         * @param options 请求选项（taskId / userId / sessionId）
         * @return A2A Event 流
         */
        @Override
        public Flux<Event> stream(List<Msg> msgs, AgentRequestOptions options) {
            String taskId = options == null ? null : options.getTaskId();
            RuntimeContext ctx = buildContext(options);
            ReActAgent delegate = harnessAgent.getDelegate();
            if (taskId != null && !taskId.isBlank()) {
                running.put(taskId, delegate);
            }
            return harnessAgent.streamEvents(msgs, ctx)
                    .mapNotNull(this::toA2aEvent)
                    .doFinally(sig -> {
                        if (taskId != null) {
                            running.remove(taskId);
                        }
                    });
        }

        /**
         * 中断指定 task。
         *
         * @param taskId A2A task id
         */
        @Override
        public void stop(String taskId) {
            if (taskId == null) {
                return;
            }
            ReActAgent agent = running.remove(taskId);
            if (agent != null) {
                agent.interrupt();
            }
        }

        /**
         * 从 A2A 选项构建 RuntimeContext。
         *
         * @param options 请求选项，可为 null
         * @return 非空 RuntimeContext
         */
        private RuntimeContext buildContext(AgentRequestOptions options) {
            String userId = "a2a-user";
            String sessionId = "default";
            if (options != null) {
                if (notBlank(options.getUserId())) {
                    userId = options.getUserId();
                }
                if (notBlank(options.getSessionId())) {
                    sessionId = options.getSessionId();
                } else if (notBlank(options.getTaskId())) {
                    sessionId = options.getTaskId();
                }
            }
            return RuntimeContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .build();
        }

        /**
         * 将 AgentScope AgentEvent 转为 A2A Event；无关事件返回 null。
         *
         * @param ae Agent 事件
         * @return A2A Event；可忽略时返回 null
         */
        private Event toA2aEvent(AgentEvent ae) {
            if (ae instanceof TextBlockDeltaEvent textDelta) {
                String delta = textDelta.getDelta();
                if (delta == null || delta.isEmpty()) {
                    return null;
                }
                return new Event(
                        EventType.REASONING,
                        new AssistantMessage(delta),
                        false);
            }
            if (ae instanceof AgentResultEvent resultEvent) {
                Msg result = resultEvent.getResult();
                if (result == null) {
                    return null;
                }
                return new Event(EventType.AGENT_RESULT, result, true);
            }
            return null;
        }

        /**
         * @param s 字符串
         * @return 非空白则为 true
         */
        private boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }
}
