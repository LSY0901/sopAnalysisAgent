package org.example.sopanalysisagent.util;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 从 AgentScope {@link Msg} / {@link ChatResponse} 提取纯文本。
 */
public final class MsgTexts {

    private MsgTexts() {
    }

    /**
     * 提取 Msg 中全部 TextBlock 文本（换行拼接）。
     *
     * @param msg 消息，可为 null
     * @return 文本内容；msg 为 null 时返回 null
     */
    public static String fromMsg(Msg msg) {
        if (msg == null) {
            return null;
        }
        return msg.getTextContent();
    }

    /**
     * 提取单个 ChatResponse 中的文本块。
     *
     * @param response 模型响应，可为 null
     * @return 拼接后的文本；response 为 null 时返回 null
     */
    public static String fromChatResponse(ChatResponse response) {
        if (response == null) {
            return null;
        }
        return joinTextBlocks(response.getContent());
    }

    /**
     * 按顺序拼接多个流式 ChatResponse 的文本增量。
     *
     * @param responses 响应序列，可为 null
     * @return 拼接文本；空序列返回空串
     */
    public static String fromChatResponses(Iterable<ChatResponse> responses) {
        if (responses == null) {
            return "";
        }
        return StreamSupport.stream(responses.spliterator(), false)
                .map(MsgTexts::fromChatResponse)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
    }

    /**
     * 从内容块列表中提取 TextBlock 文本并拼接。
     *
     * @param blocks 内容块，可为 null
     * @return 拼接文本；无文本块时返回空串
     */
    private static String joinTextBlocks(List<ContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        return blocks.stream()
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
    }
}
