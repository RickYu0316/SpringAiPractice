package cn.javastack.mcp.mcptools;

import io.modelcontextprotocol.common.McpTransportContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP 上下文回显工具
 */
@Slf4j
@Component
public class HttpContextInspectTool {

    /**
     * 获取当前请求的 HTTP 上下文信息
     */
    @McpTool(
            name = "inspect_http_context",
            description = "获取当前请求的 HTTP 上下文信息"
    )
    public Map<String, Object> inspectHttpContext(McpSyncRequestContext requestContext) {
        log.info("准备获取当前 HTTP 上下文信息...");
        McpTransportContext transportContext = requestContext.transportContext();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", transportContext.get("method"));
        result.put("uri", transportContext.get("path"));
        result.put("query", transportContext.get("query"));
        result.put("remoteAddr", transportContext.get("remoteAddr"));
        result.put("headers", transportContext.get("headers"));
        result.put("mcpName", "inspect_http_context");
        log.info("当前 HTTP 上下文信息: {}", result);
        return result;
    }
}
