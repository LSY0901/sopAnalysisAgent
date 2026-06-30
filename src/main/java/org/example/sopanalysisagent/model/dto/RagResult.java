package org.example.sopanalysisagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Python RAG 服务检索结果项。
 * 契约：POST {rag.base-url}/search 返回 {"results":[...]}，元素结构如下。
 * 注意：Python 侧当前不返回 id（该字段为 null）；score 可能为负（距离值，越小越相关）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagResult {

    /** Python 侧当前不返回，可为 null */
    private String id;

    private String content;

    private String source;

    /** 距离值，可能为负，越小越相关 */
    private Double score;
}
