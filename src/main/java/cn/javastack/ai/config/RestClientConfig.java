package cn.javastack.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * OpenRouter RestClient
 * 统一配置OpenRouter网关地址与公共请求头
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient openRouterRestClient(RestClient.Builder builder, OpenRouterProperties properties) {
        return builder.clone()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeaders(headers -> {
                    headers.add("HTTP-Referer", properties.getSiteUrl());
                    headers.add("X-Title", properties.getAppName());
                })
                .build();
    }

    /**
     * DeepSeek对话前缀续写（Beta）RestClient，走 /beta 接口
     */
    @Bean
    public RestClient deepSeekBetaRestClient(RestClient.Builder builder,
                                             @Value("${spring.ai.deepseek.base-url}") String baseUrl,
                                             @Value("${spring.ai.deepseek.api-key}") String apiKey) {
        return builder.clone()
                .baseUrl(baseUrl + "/beta")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
