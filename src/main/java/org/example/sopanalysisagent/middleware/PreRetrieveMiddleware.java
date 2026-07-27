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
import reactor.util.context.Context;

import java.util.List;
import java.util.function.Function;

/**
 * 强制预检索：rewrite → RAG → 注入 system prompt。
 * <p>
 * 若 {@code ctx == null}（旧 A2A Runner），通过 Reactor Context 传递 RAG，
 * 禁止往 inputMessages 注入 SYSTEM 消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreRetrieveMiddleware implements MiddlewareBase {

    /** RuntimeContext / Reactor Context 中存放 RAG 文本的 key */
    public static final String CTX_RAG = "rag_context";

    private static final String RAG_PREFIX =
            "以下是检索到的 SOP 知识（请优先依据它作答，并标注来源）：\n";

    private final QueryRewriteService queryRewriteService;
    private final PythonRagClient pythonRagClient;

    @Value("${rag.top-k:5}")
    private int topK;

    /**
     * Agent 调用前执行 rewrite + retrieve，并将结果写入上下文。
     *
     * @param agent Agent 实例
     * @param ctx   运行时上下文；旧 A2A 路径可能为 null
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
        return Mono.fromCallable(() -> retrieve(input, ctx))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(hold -> {
                    Flux<AgentEvent> downstream = next.apply(hold.input());
                    if (ctx != null || isBlank(hold.ragText())) {
                        return downstream;
                    }
                    return downstream.contextWrite(
                            Context.of(CTX_RAG, hold.ragText()));
                });
    }

    /**
     * 将预检索得到的 RAG 文本追加到 system prompt。
     *
     * @param agent         Agent 实例
     * @param ctx           运行时上下文；可能为 null
     * @param currentPrompt 当前 system prompt
     * @return 可能已追加 RAG 的 system prompt
     */
    @Override
    public Mono<String> onSystemPrompt(
            Agent agent, RuntimeContext ctx, String currentPrompt) {
        String base = currentPrompt == null ? "" : currentPrompt;
        return Mono.deferContextual(view -> {
            String rag = readRag(ctx);
            if (isBlank(rag) && view.hasKey(CTX_RAG)) {
                Object fromReactor = view.get(CTX_RAG);
                rag = fromReactor == null ? null : String.valueOf(fromReactor);
            }
            if (isBlank(rag)) {
                return Mono.just(base);
            }
            if (ctx != null) {
                ctx.put(CTX_RAG, rag);
            }
            return Mono.just(base + "\n\n" + RAG_PREFIX + rag);
        });
    }

    /**
     * 执行 rewrite + RAG；ctx 非空时写入 RuntimeContext。
     *
     * @param input 本轮输入
     * @param ctx   运行时上下文，可为 null
     * @return 检索结果与原始输入
     */
    private RetrieveHold retrieve(AgentInput input, RuntimeContext ctx) {
        String query = extractLastUserText(input.msgs());
        String rewritten = queryRewriteService.rewrite(query);
        List<RagResult> hits = pythonRagClient.search(rewritten, topK);
        String ragText = RagContextFormatter.format(hits);
        log.info("[pre-retrieve] hits={} rewritten={} ctxNull={}",
                hits.size(), rewritten, ctx == null);
        if (ctx != null && !isBlank(ragText)) {
            ctx.put(CTX_RAG, ragText);
        }
        return new RetrieveHold(input, ragText);
    }

    /**
     * 从 RuntimeContext 读取 RAG 文本。
     *
     * @param ctx 运行时上下文，可为 null
     * @return RAG 文本；无则 null
     */
    private String readRag(RuntimeContext ctx) {
        if (ctx == null) {
            return null;
        }
        Object rag = ctx.get(CTX_RAG);
        return rag == null ? null : String.valueOf(rag);
    }

    /**
     * @param s 待判断字符串
     * @return true 表示空白
     */
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
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

    /**
     * 预检索中间结果。
     *
     * @param input   原始 Agent 输入
     * @param ragText 格式化后的 RAG 文本
     */
    private record RetrieveHold(AgentInput input, String ragText) {
    }
}
