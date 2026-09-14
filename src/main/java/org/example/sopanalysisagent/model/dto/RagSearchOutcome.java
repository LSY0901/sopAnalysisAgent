package org.example.sopanalysisagent.model.dto;

import java.util.Collections;
import java.util.List;

/**
 * RAG 检索结果包装：区分“等待到底座返回”后的三种状态。
 * <p>
 * 成功有命中 / 成功但为空 / 调用失败（超时或异常，只有此时才兜底）。
 */
public final class RagSearchOutcome {

    private final List<RagResult> hits;
    private final boolean failed;
    private final boolean timeout;
    private final String error;

    private RagSearchOutcome(List<RagResult> hits, boolean failed, boolean timeout, String error) {
        this.hits = hits == null ? Collections.emptyList() : List.copyOf(hits);
        this.failed = failed;
        this.timeout = timeout;
        this.error = error;
    }

    public static RagSearchOutcome success(List<RagResult> hits) {
        return new RagSearchOutcome(hits, false, false, null);
    }

    public static RagSearchOutcome failure(boolean timeout, String error) {
        return new RagSearchOutcome(Collections.emptyList(), true, timeout, error);
    }

    public List<RagResult> getHits() {
        return hits;
    }

    /** 调用失败（超时/异常），此时才允许兜底。 */
    public boolean isFailed() {
        return failed;
    }

    /** 是否为读超时导致的失败。 */
    public boolean isTimeout() {
        return timeout;
    }

    public String getError() {
        return error;
    }

    /** 成功且有命中。 */
    public boolean hasHits() {
        return !failed && !hits.isEmpty();
    }
}
