package cn.javastack.ai.dto.openrouter;

import java.util.List;

/**
 * OpenRouter 内容审核结果
 */
public record OpenRouterModerationResult(boolean flagged, List<String> categories, String responseContent) {
}
