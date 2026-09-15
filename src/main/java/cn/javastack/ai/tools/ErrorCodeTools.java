package cn.javastack.ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

/**
 * 信息检索工具：错误码工具
 */
@Slf4j
public class ErrorCodeTools {

    /**
     * 错误码含义映射
     */
    public static final Map<String, String> ERROR_CODE_MAP = Map.of(
            "000", "成功",
            "101", "参数错误",
            "102", "认证失败",
            "103", "权限不足",
            "104", "资源未找到",
            "105", "操作失败",
            "106", "系统错误",
            "107", "服务不可用",
            "108", "请求超时"
    );

    /**
     * 获取错误码含义
     */
    @Tool(description = "获取错误码含义")
    public String getErrorDescription(@ToolParam(description = "错误码") String errorCode) {
        log.info("使用 ErrorCodeTools 获取错误码含义，errorCode：{}", errorCode);
        return ERROR_CODE_MAP.get(errorCode);
    }
}
