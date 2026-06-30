package org.example.sopanalysisagent.client;

import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.common.Constants;
import org.example.sopanalysisagent.model.dto.RagResult;
import org.example.sopanalysisagent.model.dto.RagSearchReq;
import org.example.sopanalysisagent.model.dto.RagSearchResp;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

/**
 * 调用外部 Python RAG 聚合检索服务的客户端。
 * <p>
 * Python 服务对外只暴露一个检索接口；embedding 与 rerank 均在 Python 侧内部完成，
 * Spring AI 不直接调用 embedding/rerank。
 * <p>
 * 契约（由本仓库定义，供 Python 侧实现）：<br>
 * POST {rag.base-url}/search <br>
 * 请求体：{"query": "...", "topK": 5} <br>
 * 响应体：{"results": [{"content": "...", "source": "...", "score": -0.33}]}
 */
@Slf4j
@Component
public class PythonRagClient {

    private final WebClient ragWebClient;

    public PythonRagClient(@Qualifier("ragWebClient") WebClient ragWebClient,
                           @Value("${rag.top-k:" + Constants.DEFAULT_TOP_K + "}") int defaultTopK) {
        this.ragWebClient = ragWebClient;
        this.defaultTopK = defaultTopK;
    }

    private final int defaultTopK;

    /**
     * 检索 SOP 知识库。
     *
     * @param query 已改写的检索 query
     * @param topK  返回条数；为空时使用默认值
     */
    public List<RagResult> search(String query, Integer topK) {
        int k = (topK == null || topK <= 0) ? defaultTopK : topK;
        RagSearchReq req = new RagSearchReq(query, k);
        try {
            RagSearchResp resp = ragWebClient.post()
                    .uri("/search")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(RagSearchResp.class)
                    .block();
            List<RagResult> results = resp == null ? null : resp.getResults();
            return results == null ? Collections.emptyList() : results;
        } catch (Exception e) {
            log.error("调用 RAG 服务失败 query={} topK={}", query, k, e);
            return Collections.emptyList();
        }
    }
}
