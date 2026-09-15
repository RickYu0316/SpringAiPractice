package cn.javastack.ai.controller;

import cn.javastack.ai.dto.custom.CustomChatRequest;
import cn.javastack.ai.tools.EmailTools;
import cn.javastack.ai.tools.ErrorCodeTools;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI工具调用接口", description = "AI工具调用接口")
@Slf4j
@RestController
@RequestMapping("/tool")
public class ToolCallingController {

    @Resource(name = "jdbcMemoryChatClient")
    private ChatClient jdbcMemoryChatClient;

    /**
     * 使用工具搜索工具，按需动态加载工具
     */
    @Operation(summary = "获取错误信息，发送邮件")
    @GetMapping("/errorInfoAndSendEmail")
    public String errorInfoAndSendEmail(CustomChatRequest request) {
        log.info("调用errorInfoAndSendEmail，请求参数：{}", request.toString());
        Assert.hasText(request.getConversationId(), "conversationId 不能为空");

        return jdbcMemoryChatClient.prompt(request.getMessage())
                .system(sp -> sp.param("voice", request.getVoice()))
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, request.getConversationId()))
                .tools(new ErrorCodeTools(), new EmailTools())
                .call()
                .content();
    }
}
