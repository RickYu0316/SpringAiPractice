package cn.javastack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenRouter 配置文件参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.open-router")
public class OpenRouterProperties {

    /**
     * OpenRouter 网关地址
     */
    private String baseUrl;

    /**
     * OpenRouter API Key
     */
    private String apiKey;

    /**
     * OpenRouter 应用名称（可选）
     */
    private String appName;

    /**
     * OpenRouter 应用来源地址（可选）
     */
    private String siteUrl;

    /**
     * 默认模型（未传model时使用）
     */
    private String defaultModel;

    /**
     * 聊天补全接口路径
     */
    private String chatCompletionsPath;
}
