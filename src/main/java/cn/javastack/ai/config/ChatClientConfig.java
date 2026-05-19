package cn.javastack.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

/**
 * ChatClient 多模型实现配置
 */
@Configuration
public class ChatClientConfig {

    /**
     * 默认使用 DeepSeek 构建 ChatClient.Builder
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    ChatClient.Builder chatClientBuilder(DeepSeekChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    /**
     * 默认使用 DeepSeek 构建 ChatClient
     */
    @Primary
    @Bean
    public ChatClient deepSeekChatClient(DeepSeekChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel)
                .defaultSystem(s -> s.text("DeepSeek 聊天AI助手，以 {voice} 的口吻回答问题")
                        .metadata("assistantType", "chat")
                        .metadata("model", "deepseek-v4-flash")
                        .metadata("version", "1.0"))
                .defaultUser(u -> u.text("Default user context")
                        .metadata("userId", "default-user")
                        .metadata("sessionId", "default-session")
                        .metadata("messageId", "default-message")
                        .metadata("priority", "high"))
                .build();
    }

    /**
     * 可手动使用 OpenAi ChatClient
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultSystem(s -> s.text("OpenAI 聊天AI助手，以 {voice} 的口吻回答问题")
                        .metadata("assistantType", "chat")
                        .metadata("model", "gpt-4o-mini")
                        .metadata("version", "1.0"))
                .defaultUser(u -> u.text("Default user context")
                        .metadata("userId", "default-user")
                        .metadata("sessionId", "default-session")
                        .metadata("messageId", "default-message")
                        .metadata("priority", "high"))
                .build();
    }

}
