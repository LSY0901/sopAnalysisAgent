package org.example.sopanalysisagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Python RAG 服务检索请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagSearchReq {

    private String query;

    private Integer top_k;
}
