package cn.javastack.mcp.mcptools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * 商户配置工具
 */
@Component
public class MerchantConfigTools {

    @McpTool(name = "validate_merchant_config",
            description = "校验商户支付配置是否正确。true：正确，false：错误")
    public boolean validateMerchantConfig(String merchantId) {
        return false;
    }
}
