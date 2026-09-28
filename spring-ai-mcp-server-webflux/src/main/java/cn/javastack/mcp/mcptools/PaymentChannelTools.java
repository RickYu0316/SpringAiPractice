package cn.javastack.mcp.mcptools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * 支付渠道工具
 */
@Component
public class PaymentChannelTools {

    @McpTool(name = "payment_channel",
            description = "查询支付渠道当前状态。状态 1 表示可用，0 表示不可用")
    public PaymentChannel getChannel(String paymentChannelCode) {
        return new PaymentChannel(
                paymentChannelCode,
                "ALIPAY",
                1
        );
    }

    public record PaymentChannel(String paymentChannelCode, String paymentChannel, Integer status) {
    }
}
