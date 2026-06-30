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

    @Bean("ragWebClient")
    public WebClient ragWebClient(WebClient.Builder builder,
                                  @Value("${rag.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
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
