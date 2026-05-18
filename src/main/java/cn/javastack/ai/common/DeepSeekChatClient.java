package cn.javastack.ai.common;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeepSeekChatClient {

    private final ChatClient.Builder builder;

    public ChatClient buildChatClient() {
        return builder.build();
    }
}
