package cn.javastack.ai.utils;

import cn.javastack.ai.dto.openrouter.OpenRouterChatCompletionResponse;
import cn.javastack.ai.dto.openrouter.OpenRouterChoice;
import cn.javastack.ai.dto.openrouter.OpenRouterMessage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * OpenRouter 响应解析工具
 */
public final class OpenRouterResponseUtils {

    private OpenRouterResponseUtils() {
    }

    /**
     * 从类型化聊天响应中提取文本内容
     */
    public static String extractContent(OpenRouterChatCompletionResponse response) {
        return Optional.ofNullable(response)
                .map(OpenRouterChatCompletionResponse::choices)
                .filter(choices -> !choices.isEmpty())
                .map(List::getFirst)
                .map(OpenRouterChoice::message)
                .map(OpenRouterMessage::content)
                .orElse(null);
    }

    /**
     * 从Map类型聊天响应中提取文本内容
     */
    @SuppressWarnings("unchecked")
    public static String extractContent(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        Map<String, Object> choice = (Map<String, Object>) choices.getFirst();
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        return message != null ? (String) message.get("content") : null;
    }

    /**
     * 从Map类型聊天响应中提取错误信息，无错误返回 null
     * OpenRouter错误格式：{"error":{"message":"...","code":400}}
     */
    public static String extractError(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object errorObj = response.get("error");
        if (errorObj instanceof Map<?, ?> error) {
            Object message = error.get("message");
            return Objects.requireNonNullElse(message, error).toString();
        }
        if (errorObj != null) {
            return errorObj.toString();
        }
        return null;
    }
}
