package cn.javastack.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI MCP Controller", description = "AI MCP Controller")
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class MCPController {

    private final ToolCallbackProvider tools;

    @Resource(name = "bingChatClient")
    private ChatClient bingChatClient;

    @Resource(name = "mysqlChatClient")
    private ChatClient mysqlChatClient;

    @PostConstruct
    public void init() {
        // 记录所有的 MCP 工具
        ToolCallback[] toolCallbacks = tools.getToolCallbacks();
        for (ToolCallback toolCallback : toolCallbacks) {
            log.info("MCP Tool: {}", toolCallback.getToolDefinition().name());
        }
    }

    @Operation(summary = "Bing网络搜索")
    @GetMapping("/bingSearch")
    public String bingSearch(@Parameter(description = "问题") @RequestParam(value = "message") String message) {
        return bingChatClient.prompt(message)
                .call()
                .content();
    }

    @Operation(summary = "MySQL数据库操作")
    @GetMapping("/mysqlDataProcess")
    public String mysqlDataProcess(@Parameter(description = "数据库操作") @RequestParam(value = "message") String message) {
        return mysqlChatClient.prompt(message)
                .call()
                .content();
    }
}
