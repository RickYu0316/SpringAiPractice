package cn.javastack.ai.controller;

import cn.javastack.ai.dto.custom.CustomChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Deepseek 通用Controller
 */
@Tag(name = "Deepseek 通用接口", description = "Deepseek 通用接口")
@Slf4j
@RestController
@RequestMapping("/deepseek")
@RequiredArgsConstructor
public class DeepseekCommonController {

    @Resource(name = "deepSeekChatClient")
    private ChatClient deepSeekChatClient;

    @Resource(name = "jdbcMemoryChatClient")
    private ChatClient jdbcMemoryChatClient;

    private final JdbcChatMemoryRepository jdbcMemoryRepository;

    @Value("classpath:/prompts/algorithm-system-prompt.st")
    private org.springframework.core.io.Resource algorithmSystemResource;

    /**
     * 从文件加载系统提示词
     */
    @Operation(summary = "IT算法生成")
    @GetMapping("/algorithmGen")
    public String algorithmGen(@Parameter(description = "格式") @RequestParam(value = "format", defaultValue = "txt") String format,
                               @Parameter(description = "语言") @RequestParam(value = "language", defaultValue = "java") String language,
                               @Parameter(description = "算法") @RequestParam(value = "algorithm", defaultValue = "冒泡排序算法") String algorithm) {
        log.info("调用algorithmGen 算法生成，format：{}，language：{}，algorithm：{}", format, language, algorithm);

        String systemPrompt = new SystemPromptTemplate(algorithmSystemResource).render(Map.of("format", format));
        log.info("从文件加载系统提示词：{}", systemPrompt);

        return deepSeekChatClient.prompt()
                .system(systemPrompt)
                .user(u -> u
                        .text("用 {language} 写一个 {algorithm} 算法")
                        .params(Map.of("language", language, "algorithm", algorithm)))
                .call()
                .content();
    }

    /**
     * JDBC聊天记忆，默认LLM：DeepSeek
     * 传入实时模型请求参数
     * 默认日志记录
     */
    @Operation(summary = "聊天")
    @GetMapping("/commonChat")
    public String commonChat(CustomChatRequest request) {
        log.info("调用commonChat，请求参数：{}", request.toString());
        Assert.hasText(request.getConversationId(), "conversationId 不能为空");

        // 切换模型参数
        DeepSeekChatOptions chatOptions = buildDeepSeekChatOptions(request);
        // 构建提示词
        Prompt prompt = new Prompt(request.getMessage(), chatOptions);

        return jdbcMemoryChatClient.prompt(prompt)
                .system(sp -> sp.param("voice", request.getVoice()))
                .advisors(ap -> ap.param(ChatMemory.CONVERSATION_ID, request.getConversationId()))
                .call()
                .content();
    }

    /**
     * 会话列表
     */
    @Operation(summary = "会话列表")
    @GetMapping("/conversations")
    public List<ConversationVO> conversations() {
        return jdbcMemoryRepository.findConversationIds()
                .stream()
                .sorted(Comparator.reverseOrder())
                .map(id -> new ConversationVO(id, title(jdbcMemoryRepository.findByConversationId(id))))
                .toList();
    }

    /**
     * 单个会话历史消息
     */
    @Operation(summary = "单个会话历史消息")
    @GetMapping("/messages")
    public List<MessageVO> messages(@Parameter(description = "会话ID") @RequestParam String conversationId) {
        return jdbcMemoryRepository.findByConversationId(conversationId)
                .stream()
                .map(m -> new MessageVO(m.getMessageType().getValue(), m.getText()))
                .toList();
    }

    /**
     * 删除会话
     */
    @Operation(summary = "删除会话")
    @DeleteMapping("/deleteConversation")
    public void deleteConversation(@Parameter(description = "会话ID") @RequestParam String conversationId) {
        jdbcMemoryRepository.deleteByConversationId(conversationId);
    }

    /**
     * 生成会话标题
     */
    private String title(List<Message> messages) {
        return messages.stream()
                .filter(m -> m.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("新会话");
    }

    /**
     * 会话视图对象
     */
    public record ConversationVO(String id, String title) {
    }

    /**
     * 消息视图对象
     */
    public record MessageVO(String role, String content) {
    }

    /**
     * 构建 DeepSeekChatOptions
     */
    private static DeepSeekChatOptions buildDeepSeekChatOptions(CustomChatRequest request) {
        DeepSeekChatOptions.Builder builder = DeepSeekChatOptions.builder();
        builder.maxTokens(request.getMaxTokens());
        return builder.build();
    }

}
