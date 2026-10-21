package org.example.sopanalysisagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP 工具 sop_consult 的结构化返回结果 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpConsultResult {

    /**
     * Agent 给出的详细回复文本（包含步骤、规范与排查建议）。
     */
    private String answer;

    /**
     * 本次交互的会话 ID（支持外部多轮连续对话时透传）。
     */
    private String sessionId;

    /**
     * 引用的 SOP 文档或知识库切片来源列表。
     */
    private List<RagReference> references;

    /**
     * 若本轮触发了建单操作，则包含 MES 返回的工单号；未建单时为 null。
     */
    private String workOrderNo;

    /**
     * SOP 引用条目。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagReference {
        private String source;
        private Double score;
    }
}
