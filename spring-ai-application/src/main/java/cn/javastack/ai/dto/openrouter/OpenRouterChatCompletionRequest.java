package cn.javastack.ai.dto.openrouter;

import java.util.List;

/**
 * OpenRouter 聊天请求体
 */
public record OpenRouterChatCompletionRequest(String model, List<OpenRouterMessage> messages) {
}
