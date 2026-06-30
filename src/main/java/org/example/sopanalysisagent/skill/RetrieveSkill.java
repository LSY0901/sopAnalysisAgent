package org.example.sopanalysisagent.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.client.PythonRagClient;
import org.example.sopanalysisagent.model.dto.RagResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 检索技能：调用 RAG 服务取回 SOP 知识片段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrieveSkill {

    private final PythonRagClient pythonRagClient;

    @Value("${rag.top-k:5}")
    private int topK;

    public List<RagResult> retrieve(String query) {
        List<RagResult> results = pythonRagClient.search(query, topK);
        log.debug("[retrieve] query={} 命中 {} 条", query, results.size());
        return results;
    }
}
