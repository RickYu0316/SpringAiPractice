package cn.javastack.ai.controller;

import cn.javastack.ai.domain.UserAddress;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Deepseek 聊天控制器
 */
@Slf4j
@RestController
@RequestMapping("/deepseek")
@RequiredArgsConstructor
public class DeepseekController {
    private static final Logger logger = LoggerFactory.getLogger(DeepseekController.class);

    private final DeepSeekChatModel chatModel;
    private final ChatClient deepSeekChatClient;
    @Resource(name = "openAiChatClient")
    private final ChatClient openAiChatClient;

    @GetMapping("/modelChat")
    public String modelChat(@RequestParam(value = "message") String message) {
        logger.info("调用modelChat，输入问题：{}", message);
        return chatModel.call(message);
    }

    @GetMapping("/clientChat")
    public String clientChat(@RequestParam(value = "message") String message,
                             @RequestParam(value = "voice", required = false, defaultValue = "聊天AI助手") String voice) {
        logger.info("调用clientChat，输入问题：{}，口吻：{}", message, voice);
        return deepSeekChatClient.prompt(message)
                .system(sp -> sp.param("voice", voice))
                .call()
                .content();
    }

    @GetMapping(value = "/chatStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chatStream(@RequestParam(value = "message") String message) {
        logger.info("调用chatStream，输入问题：{}", message);
        Prompt prompt = new Prompt(new UserMessage(message));
        return chatModel.stream(prompt);
    }

    @GetMapping(value = "/chatStreamText", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, String>> chatStreamText(@RequestParam(value = "message") String message,
                                                    @RequestParam(value = "voice", required = false, defaultValue = "聊天AI助手") String voice) {
        logger.info("调用chatStreamText，输入问题：{}，口吻：{}", message, voice);
        return deepSeekChatClient.prompt()
                .system(sp -> sp.param("voice", voice))
                .user(u -> u.text(message)
                        .metadata("userId", "100001"))
                .stream()
                .chatResponse()
                .mapNotNull(r -> r.getResult().getOutput().getText())
                .filter(StringUtils::hasLength)
                .map(text -> Collections.singletonMap("context", text));
    }

    private List<UserAddress> getUserServiceList() {
        return deepSeekChatClient.prompt()
                .user("获取最近活跃的前 5 个用户的住址列表")
                .call()
                .entity(new ParameterizedTypeReference<List<UserAddress>>() {
                });
    }

    private List<UserAddress> getUserServiceList(String message) {
        return deepSeekChatClient.prompt()
                .user(message)
                .call()
                .entity(new ParameterizedTypeReference<List<UserAddress>>() {
                });
    }
}
