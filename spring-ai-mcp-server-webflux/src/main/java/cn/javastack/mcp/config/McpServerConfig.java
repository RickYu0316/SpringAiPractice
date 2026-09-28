package cn.javastack.mcp.config;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStreamableServerTransportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class McpServerConfig {

    /**
     * WebFlux Streamable-HTTP 传输层
     * contextExtractor 从 ServerRequest 中抽取 HTTP上下文供 @McpTool 读取
     */
    @Bean
    public WebFluxStreamableServerTransportProvider webFluxStreamableServerTransportProvider(
            McpServerStreamableHttpProperties properties,
            JsonMapper jsonMapper) {
        return WebFluxStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .messageEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .contextExtractor(serverRequest -> {
                    Map<String, Object> httpContext = new LinkedHashMap<>();
                    httpContext.put("method", serverRequest.method().name());
                    httpContext.put("path", serverRequest.path());
                    httpContext.put("query", serverRequest.uri().getQuery());
                    httpContext.put("remoteAddr", serverRequest.remoteAddress()
                            .map(java.net.InetSocketAddress::getHostString)
                            .orElse("unknown"));
                    httpContext.put("headers",
                            serverRequest.headers().asHttpHeaders().toSingleValueMap().keySet().stream().toList());
                    return McpTransportContext.create(httpContext);
                })
                .build();
    }
}
