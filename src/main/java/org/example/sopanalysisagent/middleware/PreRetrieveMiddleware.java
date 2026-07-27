package org.example.sopanalysisagent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.client.PythonRagClient;
import org.example.sopanalysisagent.model.dto.RagResult;
import org.example.sopanalysisagent.service.QueryRewriteService;
import org.example.sopanalysisagent.util.MsgTexts;
import org.example.sopanalysisagent.util.RagContextFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Function;

/**
 * 强制预检索：rewrite → RAG → 注入 system prompt。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreRetrieveMiddleware implements MiddlewareBase {

    /** RuntimeContext 中存放 RAG 文本的 key */
    public static final String CTX_RAG = "rag_context";

    private final QueryRewriteService queryRewriteService;
    private final PythonRagClient pythonRagClient;

    @Value("${rag.top-k:5}")
    private int topK;

    /**
     * Agent 调用前执行 rewrite + retrieve，并将结果写入上下文。
     *
     * @param agent Agent 实例
     * @param ctx   运行时上下文
     * @param input 本轮输入消息
     * @param next  下游中间件/执行链
     * @return 下游事件流
     */
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        // rewrite + RAG 为阻塞 IO，放到 boundedElastic，避免占满 event loop
        return Mono.fromCallable(() -> {
                    String query = extractLastUserText(input.msgs());
                    String rewritten = queryRewriteService.rewrite(query);
                    List<RagResult> hits =
                            pythonRagClient.search(rewritten, topK);
                    String ragText = RagContextFormatter.format(hits);
                    ctx.put(CTX_RAG, ragText);
                    log.info("[pre-retrieve] hits={} rewritten={}",
                            hits.size(), rewritten);
                    return input;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(next::apply);
    }

    /**
     * 将预检索得到的 RAG 文本追加到 system prompt。
     *
     * @param agent         Agent 实例
     * @param ctx           运行时上下文
     * @param currentPrompt 当前 system prompt
     * @return 可能已追加 RAG 的 system prompt
     */
    @Override
    public Mono<String> onSystemPrompt(
            Agent agent, RuntimeContext ctx, String currentPrompt) {
        Object rag = ctx.get(CTX_RAG);
        if (rag == null || String.valueOf(rag).isBlank()) {
            return Mono.just(currentPrompt == null ? "" : currentPrompt);
        }
        String base = currentPrompt == null ? "" : currentPrompt;
        String appended = base
                + "\n\n以下是检索到的 SOP 知识（请优先依据它作答，并标注来源）：\n"
                + rag;
        return Mono.just(appended);
    }

    /**
     * 从消息列表中提取最后一条用户文本。
     *
     * @param msgs 输入消息，可为 null
     * @return 用户文本；找不到时返回空串
     */
    private String extractLastUserText(List<Msg> msgs) {
        if (msgs == null || msgs.isEmpty()) {
            return "";
        }
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Msg msg = msgs.get(i);
            if (msg == null) {
                continue;
            }
            if (msg.getRole() == MsgRole.USER
                    || msg instanceof UserMessage) {
                String text = MsgTexts.fromMsg(msg);
                return text == null ? "" : text.trim();
            }
        }
        Msg last = msgs.get(msgs.size() - 1);
        String text = MsgTexts.fromMsg(last);
        return text == null ? "" : text.trim();
    }
}
