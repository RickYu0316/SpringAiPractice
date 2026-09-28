package cn.javastack.ai.advisors;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * 默认日志记录 Advisor
 */
@Slf4j
@Component
public class DefaultLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    /**
     * 设置 Advisor名称
     */
    @Override
    public @NonNull String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 设置 Advisor顺序
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 非流式调用日志记录
     */
    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        logRequest(chatClientRequest);
        long start = System.nanoTime();
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        logResponse(chatClientResponse, (System.nanoTime() - start) / 1_000_000L);
        return chatClientResponse;
    }

    /**
     * 流式调用日志记录
     */
    @Override
    public @NonNull Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        logRequest(chatClientRequest);
        long start = System.nanoTime();
        Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);
        // ChatClientMessageAggregator为工具类，把Flux的响应聚合为一个单一的ChatClientResponse
        return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses, response ->
                logResponse(response, (System.nanoTime() - start) / 1_000_000L));
    }

    /**
     * 记录请求参数
     */
    private void logRequest(ChatClientRequest request) {
        log.info("记录请求参数: {}", request.prompt().getUserMessage());
    }

    /**
     * 记录响应参数
     */
    private void logResponse(ChatClientResponse chatClientResponse, long elapsedMillis) {
        assert chatClientResponse.chatResponse() != null;
        String model = chatClientResponse.chatResponse().getMetadata().getModel();
        String content = Objects.requireNonNull(chatClientResponse.chatResponse().getResult()).getOutput().toString();
        log.info("记录响应参数: 模型={}, 耗时={}ms, 响应内容={}", model, elapsedMillis, content);
    }
}
