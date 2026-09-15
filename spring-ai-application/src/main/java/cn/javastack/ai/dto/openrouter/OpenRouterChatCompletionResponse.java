package cn.javastack.ai.dto.openrouter;

import java.util.List;

/**
 * OpenRouter 聊天响应体
 */
public record OpenRouterChatCompletionResponse(List<OpenRouterChoice> choices) {
}
