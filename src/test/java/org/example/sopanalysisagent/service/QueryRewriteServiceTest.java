package org.example.sopanalysisagent.service;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.example.sopanalysisagent.util.PromptLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QueryRewriteService 单元测试（Mock Model，不调真实 LLM）。
 */
@ExtendWith(MockitoExtension.class)
class QueryRewriteServiceTest {

    @Mock
    private Model model;

    @Mock
    private PromptLoader promptLoader;

    private QueryRewriteService service;

    @BeforeEach
    void setUp() {
        service = new QueryRewriteService(model, promptLoader, 0.2);
    }

    /**
     * 成功改写时应返回模型文本并去掉首尾空白。
     */
    @Test
    void rewriteReturnsModelText() {
        when(promptLoader.loadClasspath("rewrite.txt"))
                .thenReturn("改写：{query}");
        ChatResponse response = ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("  设备断电步骤  ").build()))
                .build();
        when(model.stream(anyList(), isNull(), any(GenerateOptions.class)))
                .thenReturn(Flux.just(response));

        String result = service.rewrite("怎么断电？");

        assertEquals("设备断电步骤", result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Msg>> msgsCaptor = ArgumentCaptor.forClass(List.class);
        verify(model).stream(msgsCaptor.capture(), isNull(), any(GenerateOptions.class));
        List<Msg> sent = msgsCaptor.getValue();
        assertEquals(1, sent.size());
        assertEquals("改写：怎么断电？", sent.get(0).getTextContent());
    }

    /**
     * 模型抛错时应回退原始 query。
     */
    @Test
    void rewriteFallsBackOnError() {
        when(promptLoader.loadClasspath("rewrite.txt"))
                .thenReturn("改写：{query}");
        when(model.stream(anyList(), isNull(), any(GenerateOptions.class)))
                .thenReturn(Flux.error(new RuntimeException("boom")));

        assertEquals("原始问题", service.rewrite("原始问题"));
    }

    /**
     * 模型返回空白时应回退原始 query。
     */
    @Test
    void rewriteFallsBackOnBlank() {
        when(promptLoader.loadClasspath("rewrite.txt"))
                .thenReturn("改写：{query}");
        ChatResponse response = ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("   ").build()))
                .build();
        when(model.stream(anyList(), isNull(), any(GenerateOptions.class)))
                .thenReturn(Flux.just(response));

        assertEquals("原始问题", service.rewrite("原始问题"));
    }
}
