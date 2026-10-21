package org.example.sopanalysisagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP Server 配置属性（无状态模式）
 */
@Data
@Component
@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    /**
     * 是否开启 MCP Server 端点，默认 true
     */
    private boolean enabled = true;

    /**
     * MCP Server 暴露的服务名称
     */
    private String name = "SOP-Analysis-MCP-Server";

    /**
     * MCP Server 版本
     */
    private String version = "1.0.0";

    /**
     * 无状态 MCP HTTP 接入端点，默认 /mcp
     */
    private String endpoint = "/mcp";
}
