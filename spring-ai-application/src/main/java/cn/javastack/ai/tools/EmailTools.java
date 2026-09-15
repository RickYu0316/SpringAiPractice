package cn.javastack.ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 动作执行工具：发送邮件工具
 */
@Slf4j
public class EmailTools {

    /**
     * 发送邮件
     */
    @Tool(description = "根据错误码判断并发送邮件")
    public void sendEmail(@ToolParam(description = "错误码") String errorCode) {
        if (!"000".equals(errorCode)) {
            log.info("发生错误：{}，发送邮件", errorCode);
            return;
        }
        log.info("没有错误：{}，不发邮件", errorCode);
    }
}
