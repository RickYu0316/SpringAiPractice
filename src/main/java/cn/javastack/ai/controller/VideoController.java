package cn.javastack.ai.controller;

import cn.javastack.ai.config.OpenRouterProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频 Controller
 * 视频生成：OpenRouter异步接口 /v1/videos（提交任务→轮询状态→下载）
 * 视频理解：OpenRouter /v1/chat/completions  SpringAI多模态
 */
@Tag(name = "Video 视频接口", description = "视频生成与视频理解接口")
@Slf4j
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoController {
    private final RestClient.Builder restClientBuilder;

    private final OpenRouterProperties openRouterProperties;

    @Operation(summary = "提交视频生成异步任务")
    @PostMapping("/generate")
    public Map<String, Object> generate(
            @Parameter(description = "视频描述") @RequestParam("prompt") String prompt,
            @Parameter(description = "视频模型") @RequestParam(value = "model", required = false) String model,
            @Parameter(description = "时长（秒）") @RequestParam(value = "duration", required = false) Integer duration,
            @Parameter(description = "分辨率：480p/720p/1080p") @RequestParam(value = "resolution", required = false) String resolution,
            @Parameter(description = "宽高比：16:9/9:16/1:1") @RequestParam(value = "aspectRatio", required = false) String aspectRatio) {
        log.info("提交视频生成任务，prompt：{}，model：{}", prompt, model);

        Map<String, Object> body = new HashMap<>();
        body.put("model", StringUtils.hasLength(model) ? model : openRouterProperties.getVideoModel());
        body.put("prompt", prompt);
        if (duration != null) {
            body.put("duration", duration);
        }
        if (StringUtils.hasLength(resolution)) {
            body.put("resolution", resolution);
        }
        if (StringUtils.hasLength(aspectRatio)) {
            body.put("aspect_ratio", aspectRatio);
        }

        String uri = openRouterProperties.getBaseUrl() + openRouterProperties.getVideoPath();
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
                .toEntity(new ParameterizedTypeReference<>() {
                });

        Map<String, Object> response = responseEntity.getBody();
        log.info("视频生成任务已提交，jobId：{}", response != null ? response.get("id") : null);
        return response;
    }

    @Operation(summary = "查询视频生成任务状态")
    @GetMapping("/status/{jobId}")
    public Map<String, Object> status(
            @Parameter(description = "任务ID") @PathVariable("jobId") String jobId) {
        log.info("查询视频生成任务状态，jobId：{}", jobId);

        String uri = openRouterProperties.getBaseUrl() + openRouterProperties.getVideoPath() + "/" + jobId;
        ResponseEntity<Map<String, Object>> responseEntity = restClientBuilder
                .build()
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterProperties.getApiKey().trim())
                .headers(headers -> {
                    headers.add("HTTP-Referer", openRouterProperties.getSiteUrl());
                    headers.add("X-Title", openRouterProperties.getAppName());
                })
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });

        return responseEntity.getBody();
    }

    @Operation(summary = "视频理解（多模态）")
    @PostMapping("/understand")
    public String understand(
            @Parameter(description = "视频相关问题") @RequestParam("message") String message,
            @Parameter(description = "视频URL") @RequestParam("videoUrl") String videoUrl,
            @Parameter(description = "视频模型") @RequestParam(value = "model", required = false) String model) {
        log.info("视频理解，message：{}，videoUrl：{}", message, videoUrl);

        Map<String, Object> videoContent = Map.of(
                "type", "video_url",
                "video_url", Map.of("url", videoUrl));
        Map<String, Object> textContent = Map.of(
                "type", "text",
                "text", message);

        Map<String, Object> body = new HashMap<>();
        body.put("model", StringUtils.hasLength(model) ? model : openRouterProperties.getDefaultModel());
        body.put("messages", List.of(
                Map.of("role", "user", "content", List.of(videoContent, textContent))));

        String uri = openRouterProperties.getBaseUrl() + openRouterProperties.getChatCompletionsPath();
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
                .toEntity(new ParameterizedTypeReference<>() {
                });

        return extractChatContent(responseEntity.getBody());
    }

    /**
     * 从OpenRouter聊天响应中提取文本内容
     */
    @SuppressWarnings("unchecked")
    private static String extractChatContent(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        Map<String, Object> choice = (Map<String, Object>) choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        return message != null ? (String) message.get("content") : null;
    }
}
