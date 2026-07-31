package cn.javastack.ai.controller;

import cn.javastack.ai.config.OpenRouterProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图像生成 Controller
 * OpenRouter的图片端点是 /v1/images，SpringAI OpenAI SDK的图片端点是 /images/generations，
 * 不匹配，因此不使用SpringAI的OpenAiImageModel，改为RestClient直连 OpenRouter
 */
@Tag(name = "Image 图像生成接口", description = "图像生成接口")
@Slf4j
@RestController
@RequestMapping("/image")
@RequiredArgsConstructor
public class ImageController {
    private final RestClient.Builder restClientBuilder;

    private final OpenRouterProperties openRouterProperties;

    @Operation(summary = "图像生成")
    @GetMapping("/generate")
    public Map<String, Object> generate(
            @Parameter(description = "图像描述") @RequestParam("prompt") String prompt,
            @Parameter(description = "图像模型") @RequestParam(value = "model", required = false) String model,
            @Parameter(description = "尺寸，如 1024x1024") @RequestParam(value = "size", required = false) String size,
            @Parameter(description = "质量：auto/low/medium/high") @RequestParam(value = "quality", required = false) String quality,
            @Parameter(description = "数量 1-10") @RequestParam(value = "n", required = false) Integer n) {
        log.info("调用图像生成，prompt：{}，model：{}，size：{}，quality：{}，n：{}", prompt, model, size, quality, n);

        Map<String, Object> body = new HashMap<>();
        body.put("model", StringUtils.hasLength(model) ? model : openRouterProperties.getImageModel());
        body.put("prompt", prompt);
        if (StringUtils.hasLength(size)) {
            body.put("size", size);
        }
        if (StringUtils.hasLength(quality)) {
            body.put("quality", quality);
        }
        if (n != null) {
            body.put("n", n);
        }

        String uri = openRouterProperties.getBaseUrl() + openRouterProperties.getImagePath();
        ResponseEntity<Map<String, Object>> responseEntity = restClientBuilder
                .build()
                .post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterProperties.getApiKey().trim())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .headers(headers -> {
                    headers.add("HTTP-Referer", openRouterProperties.getSiteUrl());
                    headers.add("X-Title", openRouterProperties.getAppName());
                })
                .body(body)
                .retrieve()
                .toEntity(new org.springframework.core.ParameterizedTypeReference<>() {
                });

        Map<String, Object> response = responseEntity.getBody();
        List<Map<String, Object>> images = response != null
                ? (List<Map<String, Object>>) response.get("data")
                : List.of();

        Map<String, Object> result = new HashMap<>();
        result.put("created", response != null ? response.get("created") : null);
        result.put("count", images.size());
        result.put("model", body.get("model"));
        result.put("images", images);
        return result;
    }
}
