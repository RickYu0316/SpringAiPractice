package cn.javastack.mcp.mcptools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * 异常处理建议工具
 */
@Slf4j
@Component
public class PaymentSuggestionTools {

    @McpTool(name = "generate_suggestion",
            description = "生成支付异常处理建议")
    public String generateSuggestion(String orderId, String conclusion) {
        String suggestion = "生成支付异常处理建议，订单：%s，结论：%s".formatted(orderId, conclusion);
        log.info(suggestion);
        return suggestion;
    }
}
