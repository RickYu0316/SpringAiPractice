package cn.javastack.ai.client;

import cn.hutool.core.util.StrUtil;
import cn.javastack.ai.common.GeneratedMediaCache;
import cn.javastack.ai.config.OpenRouterProperties;
import cn.javastack.ai.dto.openrouter.OpenRouterChatCompletionRequest;
import cn.javastack.ai.dto.openrouter.OpenRouterChatCompletionResponse;
import cn.javastack.ai.dto.openrouter.OpenRouterChatRequest;
import cn.javastack.ai.dto.openrouter.OpenRouterMessage;
import cn.javastack.ai.utils.OpenRouterResponseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenRouter HTTP客户端
 * 统一封装聊天、图像、视频等接口调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenRouterClient {

    /**
     * OpenRouter代码输出格式约束
     */
    private static final String CODE_BLOCK_FORMAT_PROMPT = """
            如果回答中包含代码，请务必使用 Markdown 代码块格式输出：
            ```language
            // code
            ```
            不要把代码写成普通段落。
            """;

    /**
     * Map类型响应体引用
     */
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient openRouterRestClient;

    private final OpenRouterProperties openRouterProperties;

    private final GeneratedMediaCache generatedMediaCache;

    /**
     * OpenRouter 聊天（动态模型）
     */
    public String chat(OpenRouterChatRequest request) {
        String model = StringUtils.hasLength(request.getModel())
                ? request.getModel()
                : openRouterProperties.getDefaultModel();
        try {
            OpenRouterChatCompletionRequest completionRequest = new OpenRouterChatCompletionRequest(
                    model,
                    List.of(
                            new OpenRouterMessage("system", CODE_BLOCK_FORMAT_PROMPT),
                            new OpenRouterMessage("user", request.getMessage())
                    )
            );
            OpenRouterChatCompletionResponse response = openRouterRestClient.post()
                    .uri(openRouterProperties.getChatCompletionsPath())
                    .body(completionRequest)
                    .retrieve()
                    .body(OpenRouterChatCompletionResponse.class);

            String content = OpenRouterResponseUtils.extractContent(response);
            log.info("OpenRouter调用成功，模型：{}", model);
            if (StrUtil.isBlank(content)) {
                return "OpenRouter响应中未提取到文本内容，model=%s".formatted(model);
            }
            return content;
        } catch (Exception e) {
            log.error("调用OpenRouter接口失败：{}", e.getMessage(), e);
            return "OpenRouter AI聊天功能繁忙，请稍后重试!";
        }
    }

    /**
     * 图像生成
     * OpenRouter的图片端点是 /v1/images，SpringAI OpenAI SDK的图片端点是 /images/generations，
     * 不匹配，因此不使用SpringAI的OpenAiImageModel，改为RestClient直连 OpenRouter
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generateImage(String prompt, String model, String size) {
        log.info("调用图像生成，prompt：{}，model：{}，size：{}", prompt, model, size);

        Map<String, Object> body = new HashMap<>();
        body.put("model", StringUtils.hasLength(model) ? model : openRouterProperties.getImageModel());
        body.put("prompt", prompt);
        if (StringUtils.hasLength(size)) {
            body.put("size", size);
        }

        Map<String, Object> response = openRouterRestClient.post()
                .uri(openRouterProperties.getImagePath())
                .body(body)
                .retrieve()
                .body(MAP_TYPE);

        List<Map<String, Object>> images = response != null
                ? (List<Map<String, Object>>) response.get("data")
                : List.of();

        Map<String, Object> result = new HashMap<>();
        result.put("created", response != null ? response.get("created") : null);
        result.put("count", images.size());
        result.put("model", body.get("model"));
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        List<String> urls = new ArrayList<>();
        for (Map<String, Object> image : images) {
            String b64 = (String) image.get("b64_json");
            if (b64 == null) {
                continue;
            }
            String mediaType = image.get("media_type") != null
                    ? image.get("media_type").toString()
                    : "image/png";
            byte[] data = Base64.getDecoder().decode(b64);
            urls.add(base + "/image/download/" + generatedMediaCache.store(data, mediaType));
        }
        result.put("urls", urls);
        return result;
    }

    /**
     * 视频生成异步任务
     * 视频生成：OpenRouter异步接口 /v1/videos（提交任务→轮询状态→下载）
     */
    public Map<String, Object> generateVideo(String prompt, String model, Integer duration, String resolution, String aspectRatio) {
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

        Map<String, Object> response = openRouterRestClient.post()
                .uri(openRouterProperties.getVideoPath())
                .body(body)
                .retrieve()
                .body(MAP_TYPE);
        log.info("视频生成任务已提交，jobId：{}", response != null ? response.get("id") : null);
        return response;
    }

    /**
     * 查询视频生成任务状态
     */
    public Map<String, Object> getVideoStatus(String jobId) {
        log.info("查询视频生成任务状态，jobId：{}", jobId);
        return openRouterRestClient.get()
                .uri(openRouterProperties.getVideoPath() + "/{jobId}", jobId)
                .retrieve()
                .body(MAP_TYPE);
    }

    /**
     * 视频理解（多模态）
     * 视频理解：OpenRouter /v1/chat/completions SpringAI多模态
     */
    public String understandVideo(String message, String videoUrl, String model) {
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

        Map<String, Object> response = openRouterRestClient.post()
                .uri(openRouterProperties.getChatCompletionsPath())
                .body(body)
                .retrieve()
                .body(MAP_TYPE);
        return OpenRouterResponseUtils.extractContent(response);
    }
}
