package cn.javastack.mcp.mcptools;

import cn.javastack.mcp.dto.custom.PexelsSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Pexels图片搜索 MCP工具类
 */
@Slf4j
@Component
public class PexelsMcpTools {

    private static final String BASE_URL = "https://api.pexels.com/v1/search";
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${PEXELS_API_KEY:}")
    private String pexelsApiKey;

    @McpTool(
            name = "image_search",
            description = "从 Pexels 搜索图片"
    )
    public Map<String, Object> searchImages(PexelsSearchRequest request) {
        log.info("开始搜索图片，参数：{}", request);

        // 校验环境变量
        if (!StringUtils.hasText(pexelsApiKey)) {
            return Map.of("error", "PEXELS_API_KEY 环境变量未设置");
        }

        try {
            String requestUrl = buildRequestUrl(request);
            log.info("请求 PexelsAPI，URL：{}", requestUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", pexelsApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // 预编码URL需用 URI 传递，避免RestTemplate对 % 二次编码
            Map<?, ?> response = restTemplate.exchange(URI.create(requestUrl), HttpMethod.GET, entity, Map.class).getBody();
            if (response != null) {
                log.info("搜索图片完成，结果数：{}", response.get("total_results"));
                return Map.of(
                        "images", response.get("photos"),
                        "total_results", response.get("total_results"),
                        "query", request.getMessage(),
                        "request_url", requestUrl
                );
            }
        } catch (RestClientException e) {
            log.error("搜索图片报错", e);
        }
        log.info("搜索图片完成，结果为空");
        return Map.of("info", "搜索图片完成，结果为空");
    }

    /**
     * 构建 PexelsAPI 请求 URL
     */
    private String buildRequestUrl(PexelsSearchRequest request) {
        StringBuilder sb = new StringBuilder(BASE_URL);
        sb.append("?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : request.toQueryParams().entrySet()) {
            if (!first) {
                sb.append("&");
            }
            first = false;
            sb.append(entry.getKey())
              .append("=")
              .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
