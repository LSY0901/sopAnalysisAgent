package org.example.sopanalysisagent.util;

import org.example.sopanalysisagent.model.dto.RagResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 将 RAG 命中结果格式化为可注入 prompt 的文本。
 */
public final class RagContextFormatter {

    private RagContextFormatter() {
    }

    /**
     * 格式化检索片段；空列表返回空串。
     */
    public static String format(List<RagResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        return results.stream()
                .map(r -> "- (来源:" + r.getSource() + ") " + r.getContent())
                .collect(Collectors.joining("\n"));
    }
}
