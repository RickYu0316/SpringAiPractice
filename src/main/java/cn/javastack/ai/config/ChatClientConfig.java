package cn.javastack.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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
    @Value("${spring.ai.deepseek.chat.model}")
    private String deepSeekModel;

    /**
     * OpenAI 聊天模型
     */
    @Value("${spring.ai.openai.chat.model}")
    private String openAiModel;

    /**
     * Ollama 聊天模型
     */
    @Value("${spring.ai.ollama.chat.model}")
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
        return buildChatClient(deepSeekChatModel, "DeepSeek 聊天AI助手，以 {voice} 的口吻回答问题", deepSeekModel);
    }

    /**
     * 可手动使用 OpenAiChatClient（通过OpenRouter网关访问OpenAI模型）
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        return buildChatClient(openAiChatModel, "OpenAI 聊天AI助手，以 {voice} 的口吻回答问题", openAiModel);
    }

    /**
     * 本地OllamaChatClient
     */
    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        return buildChatClient(ollamaChatModel, "本地Ollama AI助手，以 {voice} 的口吻回答问题", ollamaModel);
    }

    /**
     * 统一构建带默认System上下文的 ChatClient
     */
    private ChatClient buildChatClient(ChatModel chatModel, String systemText, String modelName) {
        return ChatClient.builder(chatModel)
                .defaultSystem(s -> s.text(systemText)
                        .metadata("model", modelName))
                .build();
    }

}
