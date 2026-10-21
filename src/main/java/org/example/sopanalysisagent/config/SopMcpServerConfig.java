package org.example.sopanalysisagent.config;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sopanalysisagent.service.SopMcpService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * 无状态 MCP Server 装配配置：基于 HttpServletStatelessServerTransport 与 McpServer。
 * <p>
 * 遵循 MCP 最新无状态规范：外部通过单个 HTTP POST 端点（/mcp）交互，服务端无长连接连接状态。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SopMcpServerConfig {

    private static final String INPUT_SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "现场操作咨询、规程查询、设备故障现象描述或报修请求"
                },
                "session_id": {
                  "type": "string",
                  "description": "多轮会话ID。若需延续上一轮对话请传入上一轮返回的 sessionId；留空或不传则自动开启新会话"
                }
              },
              "required": ["query"]
            }
            """;

    private final McpServerProperties properties;
    private final SopMcpService sopMcpService;

    @Bean
    public HttpServletStatelessServerTransport mcpStatelessTransport() {
        return HttpServletStatelessServerTransport.builder()
                .messageEndpoint(properties.getEndpoint())
                .build();
    }

    @Bean
    public McpStatelessAsyncServer mcpStatelessAsyncServer(
            HttpServletStatelessServerTransport transport) {
        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

        McpSchema.Tool consultTool = McpSchema.Tool.builder()
                .name("sop_consult")
                .description("企业级 SOP 知识咨询与工单处理智能体。基于企业操作规程与设备手册给出精确、可执行的操作步骤；当涉及设备报修、派单时自动在 MES 系统创建工单并返回工单号。")
                .inputSchema(jsonMapper, INPUT_SCHEMA_JSON)
                .build();

        log.info("初始化无状态 MCP Server: name={}, endpoint={}",
                properties.getName(), properties.getEndpoint());

        return McpServer.async(transport)
                .serverInfo(properties.getName(), properties.getVersion())
                .toolCall(consultTool, (context, req) -> {
                    Map<String, Object> args = req.arguments();
                    return sopMcpService.consult(args)
                            .map(json -> new McpSchema.CallToolResult(
                                    List.of(new McpSchema.TextContent(json)), false))
                            .onErrorResume(e -> {
                                log.error("无状态 MCP 工具 sop_consult 执行异常: {}", e.getMessage(), e);
                                String errJson = "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}";
                                return Mono.just(new McpSchema.CallToolResult(
                                    List.of(new McpSchema.TextContent(errJson)), true));
                            });
                })
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServletRegistrationBean(
            HttpServletStatelessServerTransport transport) {
        ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
                new ServletRegistrationBean<>(transport, properties.getEndpoint());
        registration.setName("mcpStatelessServlet");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }

    /**
     * 兼容性 Filter：确保请求即使未带全 text/event-stream 也能兼容 MCP 规范的 Accept 校验。
     */
    @Bean
    public FilterRegistrationBean<Filter> mcpHeaderNormalizationFilter() {
        Filter filter = (request, response, chain) -> {
            if (request instanceof HttpServletRequest httpRequest) {
                String accept = httpRequest.getHeader("Accept");
                if (accept == null || !accept.contains("text/event-stream") || !accept.contains("application/json")) {
                    HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(httpRequest) {
                        @Override
                        public String getHeader(String name) {
                            if ("Accept".equalsIgnoreCase(name)) {
                                return "application/json, text/event-stream";
                            }
                            return super.getHeader(name);
                        }

                        @Override
                        public Enumeration<String> getHeaders(String name) {
                            if ("Accept".equalsIgnoreCase(name)) {
                                return Collections.enumeration(List.of("application/json", "text/event-stream"));
                            }
                            return super.getHeaders(name);
                        }
                    };
                    chain.doFilter(wrapper, response);
                    return;
                }
            }
            chain.doFilter(request, response);
        };

        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(properties.getEndpoint());
        registration.setName("mcpHeaderNormalizationFilter");
        registration.setOrder(1);
        return registration;
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
