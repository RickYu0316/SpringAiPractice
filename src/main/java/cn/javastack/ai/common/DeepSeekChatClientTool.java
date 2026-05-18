package cn.javastack.ai.common;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 构建通用的 DeepSeekChatClient
 */
@Component
@RequiredArgsConstructor
public class DeepSeekChatClientTool {

    private final ChatClient.Builder builder;

    public ChatClient buildChatClient() {
        return builder.build();
    }
}
