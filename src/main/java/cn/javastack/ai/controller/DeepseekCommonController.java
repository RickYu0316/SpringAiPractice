package cn.javastack.ai.controller;

import cn.javastack.ai.advisors.DefaultLoggerAdvisor;
import cn.javastack.ai.dto.custom.CustomChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @Value("classpath:/prompts/algorithm-system-prompt.st")
    private org.springframework.core.io.Resource algorithmSystemResource;

    private final DefaultLoggerAdvisor defaultLoggerAdvisor;

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
     * 传入实时模型请求参数
     * 默认日志记录
     */
    @Operation(summary = "聊天")
    @GetMapping("/commonChat")
    public String commonChat(CustomChatRequest request) {
        log.info("调用commonChat，请求参数：{}", request.toString());
        // 切换模型参数
        DeepSeekChatOptions chatOptions = buildDeepSeekChatOptions(request);
        // 构建提示词
        Prompt prompt = new Prompt(request.getMessage(), chatOptions);

        return deepSeekChatClient.prompt(prompt)
                .system(sp -> sp.param("voice", request.getVoice()))
                .advisors(defaultLoggerAdvisor)
                .call()
                .content();
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
