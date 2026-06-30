package org.example.sopanalysisagent.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Python RAG 服务检索响应包装体。
 * <p>
 * 实际返回形如：
 * <pre>
 * { "results": [ { "content": "...", "source": "...", "score": -0.33 } ] }
 * </pre>
 * 注意：Python 侧目前不返回 id，score 可能为负（距离值，越小越相关）。
 */
@Data
public class RagSearchResp {

    @JsonProperty("results")
    private List<RagResult> results;
}
