package cn.javastack.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ollama本地大模型 Controller
 */
@Tag(name = "Ollama 本地模型接口", description = "Ollama 本地大模型接口")
@Slf4j
@RestController
@RequestMapping("/ollama")
public class OllamaController {

    @Resource(name = "ollamaChatClient")
    private ChatClient ollamaChatClient;

    @Operation(summary = "本地聊天")
    @GetMapping("/chat")
    public String chat(@Parameter(description = "输入问题") @RequestParam(value = "message") String message,
                       @Parameter(description = "口吻") @RequestParam(value = "voice", required = false, defaultValue = "聊天AI助手") String voice) {
        log.info("调用Ollama本地聊天，message：{}，voice：{}", message, voice);
        return ollamaChatClient.prompt(message)
                .system(sp -> sp.param("voice", voice))
                .call()
                .content();
    }
}
