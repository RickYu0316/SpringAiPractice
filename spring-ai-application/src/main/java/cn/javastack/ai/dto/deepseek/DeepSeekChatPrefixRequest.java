package cn.javastack.ai.dto.deepseek;

import java.util.List;

/**
 * DeepSeek对话前缀续写请求体（Beta）
 * messages最后一条消息：role=assistant 且 prefix=true，模型强制以此前缀续写
 */
public record DeepSeekChatPrefixRequest(String model, List<Message> messages, List<String> stop) {

    /**
     * 对话前缀续写消息体
     */
    public record Message(String role, String content, Boolean prefix) {
    }
}
