package cn.javastack.ai.controller;

import cn.javastack.ai.client.OpenRouterClient;
import cn.javastack.ai.dto.openrouter.OpenRouterModerationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "内容安全审核 Controller", description = "内容安全审核 Controller")
@Slf4j
@RestController
@RequestMapping("/moderation")
@RequiredArgsConstructor
public class ModerationController {

    /**
     * OpenRouter 客户端
     */
    private final OpenRouterClient openRouterClient;

    @Operation(summary = "内容安全审核")
    @GetMapping("/checkText")
    public OpenRouterModerationResult checkText(@Parameter(description = "待审查内容") @RequestParam("message") String message) {
        return openRouterClient.moderate(message);
    }
}
