package cn.javastack.mcp.mcptools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * 风控规则工具
 */
@Component
public class RiskRuleTools {

    @McpTool(name = "risk_rule",
            description = "查询是否命中支付风控策略。true：命中，false：未命中")
    public boolean isRiskIntercepted(String orderId) {
        return true;
    }
}
