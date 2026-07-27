package org.example.sopanalysisagent.util;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Msg / ChatResponse 文本提取单元测试。
 */
class MsgTextsTest {

    /**
     * null Msg 应返回 null。
     */
    @Test
    void fromMsgNullReturnsNull() {
        assertNull(MsgTexts.fromMsg(null));
    }

    /**
     * 应从 Msg 的 TextBlock 拼接文本。
     */
    @Test
    void fromMsgReturnsTextContent() {
        Msg msg = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("改写结果").build())
                .build();
        assertEquals("改写结果", MsgTexts.fromMsg(msg));
    }

    /**
     * null ChatResponse 应返回 null。
     */
    @Test
    void fromChatResponseNullReturnsNull() {
        assertNull(MsgTexts.fromChatResponse(null));
    }

    /**
     * 应从 ChatResponse 内容块提取文本。
     */
    @Test
    void fromChatResponseExtractsTextBlocks() {
        ChatResponse response = ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("检索查询").build()))
                .build();
        assertEquals("检索查询", MsgTexts.fromChatResponse(response));
    }

    /**
     * 多个流式 ChatResponse 的文本应顺序拼接。
     */
    @Test
    void fromChatResponsesConcatenates() {
        ChatResponse a = ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("设备").build()))
                .build();
        ChatResponse b = ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("故障").build()))
                .build();
        assertEquals("设备故障", MsgTexts.fromChatResponses(List.of(a, b)));
    }
}
