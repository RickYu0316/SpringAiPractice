package cn.javastack.ai.controller;

import cn.javastack.ai.client.OpenRouterClient;
import cn.javastack.ai.dto.openrouter.OpenRouterChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenRouter 聊天Controller
 */
@Tag(name = "OpenRouter 聊天接口", description = "OpenRouter 聊天接口")
@RestController
@RequestMapping("/openrouter")
@RequiredArgsConstructor
public class OpenRouterChatController {

    private final OpenRouterClient openRouterClient;

    @Operation(summary = "OpenRouter聊天接口（动态模型）")
    @GetMapping("/chat")
    public String chat(OpenRouterChatRequest request) {
        return openRouterClient.chat(request);
    }
}
