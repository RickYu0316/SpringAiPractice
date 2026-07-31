package cn.javastack.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

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

    @Resource
    private OllamaChatModel ollamaChatModel;

    private final String ollamaImageModel = "";

    @Operation(summary = "本地聊天")
    @GetMapping("/chat")
    public String chat(@Parameter(description = "输入问题") @RequestParam(value = "message") String message,
                       @Parameter(description = "口吻") @RequestParam(value = "voice", required = false, defaultValue = "聊天AI助手") String voice) {
        log.info("调用Ollama本地聊天，message：{}", message);
        return ollamaChatClient.prompt(message)
                .system(sp -> sp.param("voice", voice))
                .call()
                .content();
    }

    @Operation(summary = "本地图片理解（视觉模型）")
    @GetMapping("/vision")
    public String vision(@Parameter(description = "图片相关问题") @RequestParam(value = "message") String message,
                         @Parameter(description = "图片地址") @RequestParam(value = "imageUrl") String imageUrl,
                         @Parameter(description = "视觉模型") @RequestParam(value = "model", required = false, defaultValue = ollamaImageModel) String model) {
        log.info("调用Ollama图片理解，message：{}，imageUrl：{}，model：{}", message, imageUrl, model);
        return ChatClient.builder(ollamaChatModel)
                .build()
                .prompt()
                .options(OllamaChatOptions.builder().model(model))
                .system("你是本地Ollama多模态助手，请仔细分析用户提供的图片并回答问题")
                .user(u -> u.text(message)
                        .media(new Media(MimeType.valueOf(MediaType.IMAGE_JPEG_VALUE), URI.create(imageUrl))))
                .call()
                .content();
    }
}
