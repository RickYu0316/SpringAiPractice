package cn.javastack.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
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
     * DeepSeek 聊天模型
     */
    @Value("${spring.ai.deepseek.chat.options.model}")
    private String deepSeekModel;

    /**
     * OpenAI 聊天模型
     */
    @Value("${spring.ai.openai.chat.options.model}")
    private String openAiModel;

    /**
     * Ollama 聊天模型
     */
    @Value("${spring.ai.ollama.chat.options.model}")
    private String ollamaModel;

    /**
     * bean未正确加载，手动加载
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

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
                        .metadata("model", deepSeekModel)
                        .metadata("version", "1.0"))
                .defaultUser(u -> u.text("Default user context")
                        .metadata("userId", "default-user")
                        .metadata("sessionId", "default-session")
                        .metadata("messageId", "default-message")
                        .metadata("priority", "high"))
                .build();
    }

    /**
     * 可手动使用 OpenAiChatClient（通过OpenRouter网关访问OpenAI模型）
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultSystem(s -> s.text("OpenAI 聊天AI助手，以 {voice} 的口吻回答问题")
                        .metadata("assistantType", "chat")
                        .metadata("model", openAiModel)
                        .metadata("version", "1.0"))
                .defaultUser(u -> u.text("Default user context")
                        .metadata("userId", "default-user")
                        .metadata("sessionId", "default-session")
                        .metadata("messageId", "default-message")
                        .metadata("priority", "high"))
                .build();
    }

    /**
     * 本地OllamaChatClient
     */
    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel)
                .defaultSystem(s -> s.text("本地Ollama AI助手，以 {voice} 的口吻回答问题")
                        .metadata("assistantType", "chat")
                        .metadata("model", ollamaModel)
                        .metadata("version", "1.0"))
                .defaultUser(u -> u.text("Default user context")
                        .metadata("userId", "default-user")
                        .metadata("sessionId", "default-session")
                        .metadata("messageId", "default-message")
                        .metadata("priority", "high"))
                .build();
    }

}
