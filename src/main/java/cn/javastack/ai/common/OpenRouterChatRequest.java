package cn.javastack.ai.common;

import lombok.Data;

/**
 * OpenRouter 聊天请求参数
 */
@Data
public class OpenRouterChatRequest {

    /**
     * 消息内容
     */
    private String message;

    /**
     * 模型名称（需包含组织前缀）
     */
    private String model = "openai/gpt-4o-mini";

}
