package cn.javastack.mcp.mcptools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 支付订单工具
 */
@Component
public class PaymentOrderTools {

    @McpTool(
            name = "payment_order",
            description = "查询支付订单详情。状态 1 表示支付成功，2 表示支付失败"
    )
    public PaymentOrder getOrder(String orderId) {
        return new PaymentOrder(
                orderId,
                "10001",
                "ALIPAY",
                new BigDecimal(5000),
                2
        );
    }

    public record PaymentOrder(String orderId, String paymentChannelCode, String paymentChannel,
                               BigDecimal amount, Integer status) {
    }
}
