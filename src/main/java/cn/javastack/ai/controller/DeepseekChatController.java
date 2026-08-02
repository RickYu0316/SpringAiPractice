package cn.javastack.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deepseek 聊天Controller
 */
@Tag(name = "Deepseek 聊天接口", description = "Deepseek 聊天接口")
@Slf4j
@RestController
@RequestMapping("/deepseek")
public class DeepseekChatController {

    @Resource(name = "deepSeekChatClient")
    private ChatClient deepSeekChatClient;

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
                .mapNotNull(r -> Objects.requireNonNull(r.getResult()).getOutput().getText())
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

        // TODO 在输出内容前，拼接 "思考过程：" 和 "回答内容："
        return deepSeekChatClient.prompt(new Prompt(message))
                .system(sp -> sp.param("voice", voice))
                .user(u -> u.metadata("userId", "100001"))
                .stream()
                .chatResponse()
                .mapNotNull(response -> {
                    DeepSeekAssistantMessage assistantMessage = (DeepSeekAssistantMessage) Objects.requireNonNull(response.getResult()).getOutput();
                    // 优先输出思考过程
                    if (StringUtils.hasLength(assistantMessage.getReasoningContent())) {
                        return assistantMessage.getReasoningContent();
                    }
                    // 再输出最终回答
                    if (StringUtils.hasLength(assistantMessage.getText())) {
                        return assistantMessage.getText();
                    }

                    return null;
                })
                .map(text -> Collections.singletonMap("context", text));
    }

    @Operation(summary = "多轮思考示例")
    @GetMapping("/chatReasoning2")
    public String chatReasoning2() {
        // 第1轮推理聊天
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage("java中，写一个判断单个字符串是否为空的工具类，尽量简洁不超过30行"));
        ChatResponse response = deepSeekChatClient.prompt(new Prompt(messages)).call().chatResponse();

        Objects.requireNonNull(response, "response must not be null");
        DeepSeekAssistantMessage assistantMessage = (DeepSeekAssistantMessage) Objects.requireNonNull(response.getResult()).getOutput();
        String reasoningContent1 = assistantMessage.getReasoningContent();
        String text1 = assistantMessage.getText();
        log.info("思考过程1：{}", reasoningContent1);
        log.info("回答内容1：{}", text1);

        // 第2轮推理聊天
        messages.add(AssistantMessage.builder().content(Objects.requireNonNull(text1)).build());
        messages.add(new UserMessage("在刚才工具类的基础上，继续简化代码实现"));
        ChatResponse response2 = deepSeekChatClient.prompt(new Prompt(messages)).call().chatResponse();

        Objects.requireNonNull(response2, "response2 must not be null");
        DeepSeekAssistantMessage assistantMessage2 = (DeepSeekAssistantMessage) Objects.requireNonNull(response2.getResult()).getOutput();
        String reasoningContent2 = assistantMessage2.getReasoningContent();
        String text2 = assistantMessage2.getText();
        log.info("思考过程2：{}", reasoningContent2);
        log.info("回答内容2：{}", text2);

        return REASONING_RESULT_FORMAT.formatted(reasoningContent2, text2);
    }
}
