package cn.javastack.ai.controller;

import cn.javastack.ai.common.OpenRouterProperties;
import cn.javastack.ai.common.OpenRouterChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * OpenRouter 聊天Controller
 */
@Tag(name = "OpenRouter SpringAI 接口", description = "OpenRouter SpringAI 接口文档")
@Slf4j
@RestController
@RequestMapping("/openrouter")
@RequiredArgsConstructor
public class OpenRouterController {

    // OpenRouter代码输出格式约束
    private static final String CODE_BLOCK_FORMAT_PROMPT = """
            如果回答中包含代码，请务必使用 Markdown 代码块格式输出：
            ```language
            // code
            ```
            不要把代码写成普通段落。
            """;

    private final RestClient.Builder restClientBuilder;

    private final OpenRouterProperties openRouterProperties;

    private final ObjectMapper objectMapper;

    @Operation(summary = "OpenRouter聊天接口（动态模型）")
    @GetMapping("/chat")
    public String chat(OpenRouterChatRequest request) {
        ResponseEntity<String> responseEntity;
        try {
            String message = request.getMessage();
            String model = request.getModel();
            // OpenRouter调用大模型地址
            String uri = openRouterProperties.getBaseUrl() + openRouterProperties.getChatCompletionsPath();

            // 构建聊天请求体
            OpenRouterChatCompletionRequest completionRequest = new OpenRouterChatCompletionRequest(
                    model,
                    List.of(
                            new OpenRouterMessage("system", CODE_BLOCK_FORMAT_PROMPT),
                            new OpenRouterMessage("user", message)
                    )
            );

            // 调用OpenRouter聊天地址，读取原始文本
            responseEntity = restClientBuilder
                    .build()
                    .post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterProperties.getApiKey().trim())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .headers(headers -> {
                        headers.add("HTTP-Referer", openRouterProperties.getSiteUrl());
                        headers.add("X-Title", openRouterProperties.getAppName());
                    })
                    .body(completionRequest)
                    .retrieve()
                    .toEntity(String.class);

            // 解析OpenRouter响应，提取聊天内容
            OpenRouterChatCompletionResponse response = objectMapper.readValue(responseEntity.getBody(), OpenRouterChatCompletionResponse.class);
            String content = extractResponseContent(response);
            log.info("OpenRouter调用成功，模型：{}", model);
            if (StringUtils.isBlank(content)) {
                return "OpenRouter响应中未提取到文本内容，model=%s".formatted(model);
            }
            return content;
        } catch (Exception e) {
            log.error("调用OpenRouter接口失败：{}", e.getMessage(), e);
            return "OpenRouter AI聊天功能繁忙，请稍后重试!";
        }
    }

    /**
     * 提取聊天返回文本
     */
    private static String extractResponseContent(OpenRouterChatCompletionResponse response) {
        return Optional.ofNullable(response)
                .map(OpenRouterChatCompletionResponse::choices)
                .filter(choices -> !choices.isEmpty())
                .map(List::getFirst)
                .map(OpenRouterChoice::message)
                .map(OpenRouterMessage::content)
                .orElse(null);
    }

    /**
     * OpenRouter 聊天请求体
     */
    private record OpenRouterChatCompletionRequest(String model, List<OpenRouterMessage> messages) {
    }

    /**
     * OpenRouter 消息体
     */
    private record OpenRouterMessage(String role, String content) {
    }

    /**
     * OpenRouter 聊天响应体
     */
    private record OpenRouterChatCompletionResponse(List<OpenRouterChoice> choices) {
    }

    /**
     * OpenRouter 响应选项
     */
    private record OpenRouterChoice(OpenRouterMessage message) {
    }
}

