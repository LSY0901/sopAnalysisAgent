package org.example.sopanalysisagent.config;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 当 starter 未提供 Model Bean 时，按本地配置构建 DashScopeChatModel。
 * Task 6 会装配完整 Agent；此处仅保证改写服务可注入 Model。
 */
@Configuration
public class DashScopeModelConfig {

    /**
     * 兜底 Model：读取 agentscope.dashscope.api-key 与 sop.agent.model/temperature。
     *
     * @param apiKey      DashScope API Key
     * @param modelName   模型名
     * @param temperature 默认温度
     * @return DashScope Chat Model
     */
    @Bean
    @ConditionalOnMissingBean(Model.class)
    @ConditionalOnProperty(prefix = "agentscope.dashscope", name = "api-key")
    public Model dashScopeChatModel(
            @Value("${agentscope.dashscope.api-key}") String apiKey,
            @Value("${sop.agent.model:qwen-plus}") String modelName,
            @Value("${sop.agent.temperature:0.2}") double temperature) {
        GenerateOptions defaults = GenerateOptions.builder()
                .temperature(temperature)
                .build();
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(false)
                .defaultOptions(defaults)
                .build();
    }
}
