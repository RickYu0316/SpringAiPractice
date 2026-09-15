package cn.javastack.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.Map;

/**
 * OpenAI 聊天Controller（通过OpenRouter网关访问OpenAI模型）
 */
@Tag(name = "OpenAI 聊天接口", description = "OpenAI 聊天接口")
@Slf4j
@RestController
@RequestMapping("/openai")
public class OpenAiChatController {

    @Resource(name = "openAiChatClient")
    private ChatClient openAiChatClient;

    @Operation(summary = "client简单聊天")
    @GetMapping("/clientChat")
    public String clientChat(@Parameter(description = "输入问题") @RequestParam(value = "message") String message,
                             @Parameter(description = "口吻") @RequestParam(value = "voice", required = false, defaultValue = "聊天AI助手") String voice) {
        log.info("调用openAi clientChat，输入问题：{}。口吻：{}", message, voice);
        return openAiChatClient.prompt(message)
                .system(sp -> sp.param("voice", voice))
                .call()
                .content();
    }

    @Operation(summary = "client流式聊天")
    @GetMapping(value = "/clientStreamText", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, String>> clientStreamText(@Parameter(description = "输入问题") @RequestParam(value = "message") String message,
                                                      @Parameter(description = "口吻") @RequestParam(value = "voice", required = false, defaultValue = "聊天AI助手") String voice) {
        log.info("调用openAi clientStreamText，输入问题：{}。口吻：{}", message, voice);
        return openAiChatClient.prompt()
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
}
