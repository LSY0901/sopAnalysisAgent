package org.example.sopanalysisagent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.example.sopanalysisagent.client.PythonRagClient;
import org.example.sopanalysisagent.model.dto.RagResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 知识检索工具。供 Agent 在需要 SOP 规程时调用。
 */
@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool {

    private final PythonRagClient pythonRagClient;

    /**
     * 在企业 SOP 知识库中检索。
     *
     * @param query 检索关键词或问题
     * @param topK  返回的条数，默认 5
     * @return 格式化的检索结果文本
     */
    @Tool(name = "search_knowledge",
            description = "在企业 SOP 知识库中检索操作规程、设备手册、"
                    + "安全规范、故障排查步骤等知识。"
                    + "当需要给出具体操作步骤或规范依据时调用。")
    public String searchKnowledge(
            @ToolParam(name = "query",
                    description = "检索关键词或问题，应为清晰的设备名/操作目标/故障现象")
            String query,
            @ToolParam(name = "top_k",
                    description = "返回的条数，默认5", required = false)
            Integer topK) {
        List<RagResult> results = pythonRagClient.search(query, topK);
        if (results.isEmpty()) {
            return "未检索到相关 SOP 知识。";
        }
        return IntStream.range(0, results.size())
                .mapToObj(i -> {
                    RagResult r = results.get(i);
                    String score = r.getScore() != null
                            ? String.format("%.2f", r.getScore()) : "N/A";
                    return String.format("[%d] (来源:%s, score:%s) %s",
                            i + 1, r.getSource(), score, r.getContent());
                })
                .collect(Collectors.joining("\n"));
    }
}
