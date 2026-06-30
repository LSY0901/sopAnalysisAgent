package org.example.sopanalysisagent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 流式分片（保留结构，便于前端区分阶段/内容）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamChunk {

    private String sessionId;

    private String stage;

    private String content;

    public static ChatStreamChunk of(String sessionId, String stage, String content) {
        return new ChatStreamChunk(sessionId, stage, content);
    }
}
