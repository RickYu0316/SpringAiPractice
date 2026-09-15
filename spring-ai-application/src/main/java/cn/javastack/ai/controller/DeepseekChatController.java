package cn.javastack.ai.controller;

import cn.javastack.ai.dto.deepseek.DeepSeekChatPrefixRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deepseek 聊天Controller
 */
@Tag(name = "Deepseek 聊天接口", description = "Deepseek 聊天接口")
@Slf4j
@RestController
@RequestMapping("/deepseekchat")
public class DeepseekChatController {

    @Resource(name = "deepSeekChatClient")
    private ChatClient deepSeekChatClient;

    @Resource(name = "deepSeekBetaRestClient")
    private RestClient deepSeekBetaRestClient;

    /**
     * DeepSeek默认模型
     */
    @Value("${spring.ai.deepseek.chat.model}")
    private String deepSeekModel;

    private static final String chatCompletionsUri = "/chat/completions";

    /**
     * Map类型响应体引用
     */
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    /**
     * 推理结果输出格式
     */
    private static final String REASONING_RESULT_FORMAT = "思考过程：\n\n%s\n\n回答内容：\n\n%s";

    @Operation(summary = "client简单聊天")
    @GetMapping("/clientChat")
    public String clientChat(@Parameter(description = "输入问题") @RequestParam(value = "message") String message,
                             @Parameter(description = "口吻") @RequestParam(value = "voice", required = false, defaultValue = "聊天AI助手") String voice) {
        log.info("调用clientChat，输入问题：{}。口吻：{}", message, voice);
        return deepSeekChatClient.prompt(message)
                .system(sp -> sp.param("voice", voice))
                .call()
                .content();
    }

    @Operation(summary = "client流式聊天")
    @GetMapping(value = "/clientStreamText", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, String>> clientStreamText(@Parameter(description = "输入问题") @RequestParam(value = "message") String message,
                                                      @Parameter(description = "口吻") @RequestParam(value = "voice", required = false, defaultValue = "聊天AI助手") String voice) {
        log.info("调用clientStreamText，输入问题：{}。口吻：{}", message, voice);
        return deepSeekChatClient.prompt()
                .system(sp -> sp.param("voice", voice))
                .user(u -> u.text(message)
                        .metadata("userId", "100001"))
                .stream()
                .chatResponse()
                .mapNotNull(r -> {
                    if (r.getResult() == null) {
                        return null;
                    }
                    return r.getResult().getOutput().getText();
                })
                .filter(StringUtils::hasLength)
                .map(text -> Collections.singletonMap("context", text));
    }

    @Operation(summary = "思考过程简单聊天")
    @GetMapping("/chatReasoning")
    public String chatReasoning(@Parameter(description = "输入问题") @RequestParam(value = "message") String message,
                                @Parameter(description = "口吻") @RequestParam(value = "voice", required = false, defaultValue = "聊天AI助手") String voice) {
        log.info("调用chatReasoning，输入问题：{}。口吻：{}", message, voice);

        ChatResponse chatResponse = deepSeekChatClient.prompt(new Prompt(message))
                .system(sp -> sp.param("voice", voice))
                .call()
                .chatResponse();

        Objects.requireNonNull(chatResponse, "chatResponse must not be null");
        DeepSeekAssistantMessage assistantMessage = (DeepSeekAssistantMessage) Objects.requireNonNull(chatResponse.getResult()).getOutput();

        return REASONING_RESULT_FORMAT.formatted(assistantMessage.getReasoningContent(), assistantMessage.getText());
    }

    @Operation(summary = "思考过程流式聊天")
    @GetMapping(value = "/chatReasoningStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, String>> chatReasoningStream(@Parameter(description = "输入问题") @RequestParam(value = "message") String message,
                                                         @Parameter(description = "口吻") @RequestParam(value = "voice", required = false, defaultValue = "聊天AI助手") String voice) {
        log.info("调用chatReasoningStream，输入问题：{}。口吻：{}", message, voice);

        // 思考过程/回答内容，首次输出分片时拼接标签
        AtomicBoolean reasoningLabeled = new AtomicBoolean(false);
        AtomicBoolean answerLabeled = new AtomicBoolean(false);

        return deepSeekChatClient.prompt(new Prompt(message))
                .system(sp -> sp.param("voice", voice))
                .user(u -> u.metadata("userId", "100001"))
                .stream()
                .chatResponse()
                .mapNotNull(response -> {
                    if (response.getResult() == null) {
                        return null;
                    }
                    DeepSeekAssistantMessage assistantMessage = (DeepSeekAssistantMessage) response.getResult().getOutput();
                    // 优先输出思考过程，首次分片前拼接 "思考过程："
                    if (StringUtils.hasLength(assistantMessage.getReasoningContent())) {
                        if (reasoningLabeled.compareAndSet(false, true)) {
                            return "思考过程：\n\n" + assistantMessage.getReasoningContent();
                        }
                        return assistantMessage.getReasoningContent();
                    }
                    // 再输出最终回答，首次分片前拼接 "回答内容："
                    if (StringUtils.hasLength(assistantMessage.getText())) {
                        if (answerLabeled.compareAndSet(false, true)) {
                            return "回答内容：\n\n" + assistantMessage.getText();
                        }
                        return assistantMessage.getText();
                    }

                    return null;
                })
                .map(text -> Collections.singletonMap("context", text));
    }

    @Operation(summary = "对话前缀续写（Beta）")
    @GetMapping("/chatPrefixCompletion")
    public String chatPrefixCompletion(@Parameter(description = "输入问题") @RequestParam(value = "message") String message,
                                       @Parameter(description = "强制续写前缀") @RequestParam(value = "prefix") String prefix,
                                       @Parameter(description = "停止符，多个逗号分隔，如：\\n```") @RequestParam(value = "stop", required = false) String stop,
                                       @Parameter(description = "模型") @RequestParam(value = "model", required = false) String model) {
        log.info("调用chatPrefixCompletion，输入问题：{}。前缀：{}。停止符：{}。模型：{}", message, prefix, stop, model);

        String usedModel = StringUtils.hasLength(model) ? model : deepSeekModel;
        List<String> stopTokens = StringUtils.hasLength(stop)
                ? Arrays.stream(stop.split(",")).map(String::trim).filter(StringUtils::hasLength).toList()
                : List.of();

        DeepSeekChatPrefixRequest request = new DeepSeekChatPrefixRequest(
                usedModel,
                List.of(
                        new DeepSeekChatPrefixRequest.Message("user", message, null),
                        new DeepSeekChatPrefixRequest.Message("assistant", prefix, true)
                ),
                stopTokens.isEmpty() ? null : stopTokens
        );

        Map<String, Object> response = deepSeekBetaRestClient.post()
                .uri(chatCompletionsUri)
                .body(request)
                .retrieve()
                .body(MAP_TYPE);

        return extractContent(response);
    }

    /**
     * 从 DeepSeek 前缀续写响应中提取 choices[0].message.content
     */
    private String extractContent(Map<String, Object> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        if (choices.getFirst() instanceof Map<?, ?> choice && choice.get("message") instanceof Map<?, ?> message) {
            Object content = message.get("content");
            return content == null ? null : content.toString();
        }
        return null;
    }
}
