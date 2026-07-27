package org.example.sopanalysisagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SOP Agent 本地配置（模型名、workspace 等）。
 */
@Data
@ConfigurationProperties(prefix = "sop.agent")
public class AgentScopeProperties {

    /** DashScope 模型名，如 qwen-plus */
    private String model = "qwen-plus";

    /** 采样温度 */
    private double temperature = 0.2;

    /** Harness workspace 目录 */
    private String workspace = ".agentscope/workspace";
}
