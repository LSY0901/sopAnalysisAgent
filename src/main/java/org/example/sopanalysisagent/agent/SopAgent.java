package org.example.sopanalysisagent.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.model.dto.RagResult;
import org.example.sopanalysisagent.tool.CreateWorkOrderTool;
import org.example.sopanalysisagent.tool.SearchKnowledgeTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SOP 智能体：封装 ChatClient + 工具层，负责最终答案生成。
 * <p>
 * 工具（检索知识 / 创建工单）按调用挂载（.tools(...)），不污染共享 ChatClient 单例。
 * 检索到的 RAG 上下文作为 system 文本拼入 user prompt，引导模型结合知识库作答。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SopAgent {

    private final ChatClient chatClient;
    private final SearchKnowledgeTool searchKnowledgeTool;
    private final CreateWorkOrderTool createWorkOrderTool;

    /**
     * 流式对话。
     *
     * @param query   用户问题（建议已改写）
     * @param context RAG 检索片段
     * @param history 历史对话（交替的 user/assistant 文本，按时间顺序）
     */
    public Flux<String> chat(String query, List<RagResult> context, List<String> history) {
        String userContent = buildUserContent(query, context);
        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                .messages(toMessages(history))
                .user(userContent)
                .tools(searchKnowledgeTool, createWorkOrderTool);
        return request.stream().content();
    }

    /**
     * 同步对话（用于改写等内部调用，或非流式接口）。
     */
    public String chatSync(String query, List<RagResult> context, List<String> history) {
        String userContent = buildUserContent(query, context);
        return chatClient.prompt()
                .messages(toMessages(history))
                .user(userContent)
                .tools(searchKnowledgeTool, createWorkOrderTool)
                .call()
                .content();
    }

    private String buildUserContent(String query, List<RagResult> context) {
        if (context == null || context.isEmpty()) {
            return query;
        }
        String ctxText = context.stream()
                .map(r -> "- (来源:" + r.getSource() + ") " + r.getContent())
                .collect(Collectors.joining("\n"));
        return "以下是检索到的 SOP 知识（请优先依据它作答，并标注来源）：\n" + ctxText + "\n\n用户问题：" + query;
    }

    /**
     * 把扁平的历史文本交替转为 user/assistant 消息。
     * 约定：history 第 0 项为 user，第 1 项为 assistant，依此类推。
     */
    private List<Message> toMessages(List<String> history) {
        List<Message> messages = new ArrayList<>();
        if (history == null) {
            return messages;
        }
        for (int i = 0; i < history.size(); i++) {
            String text = history.get(i);
            if (text == null || text.isBlank()) {
                continue;
            }
            messages.add(i % 2 == 0 ? new UserMessage(text) : new AssistantMessage(text));
        }
        return messages;
    }
}
