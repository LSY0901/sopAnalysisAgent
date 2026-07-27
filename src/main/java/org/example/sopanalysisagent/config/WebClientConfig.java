package org.example.sopanalysisagent.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient 配置。为 PythonRagClient / MesClient / ErpClient 提供按 base-url 预置的客户端。
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    /**
     * RAG 专用客户端：超时单独配置，避免 /search 冷启动被误杀。
     *
     * @param baseUrl            Python RAG 服务地址
     * @param responseTimeoutSec 读超时秒数，默认 30
     * @return ragWebClient
     */
    @Bean("ragWebClient")
    public WebClient ragWebClient(
            @Value("${rag.base-url}") String baseUrl,
            @Value("${rag.response-timeout-seconds:30}") int responseTimeoutSec) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(Math.max(1, responseTimeoutSec)));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("mesWebClient")
    public WebClient mesWebClient(WebClient.Builder builder,
                                  @Value("${mes.base-url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }

    @Bean("erpWebClient")
    public WebClient erpWebClient(WebClient.Builder builder,
                                  @Value("${erp.base-url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }
}
