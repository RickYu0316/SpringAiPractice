package cn.javastack.ai.config;

import cn.javastack.ai.advisors.DefaultLoggerAdvisor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.Objects;

/**
 * ChatClient 多模型实现配置
 */
@Configuration
public class ChatClientConfig {

    /**
     * DeepSeek 聊天模型
     */
    @Value("${spring.ai.deepseek.chat.model}")
    private String deepSeekModel;

    /**
     * OpenAI 聊天模型
     */
    @Value("${spring.ai.openai.chat.model}")
    private String openAiModel;

    /**
     * Ollama 聊天模型
     */
    @Value("${spring.ai.ollama.chat.model}")
    private String ollamaModel;

    @Value("${MYSQL_HOST}")
    private String mysqlHost;

    @Value("${MYSQL_USER}")
    private String mysqlUser;

    @Value("${MYSQL_PASSWORD}")
    private String mysqlPassword;

    @Value("${MYSQL_DATABASE}")
    private String mysqlDatabase;

    @Resource
    private DefaultLoggerAdvisor defaultLoggerAdvisor;

    private static final int maxMessages = 50;

    /**
     * bean未正确加载，手动加载
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * 默认使用 DeepSeek 构建 ChatClient.Builder
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    ChatClient.Builder chatClientBuilder(DeepSeekChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    /**
     * 默认使用 DeepSeek 构建 ChatClient
     */
    @Primary
    @Bean
    public ChatClient deepSeekChatClient(DeepSeekChatModel deepSeekChatModel) {
        return buildChatClient(deepSeekChatModel, "DeepSeek 聊天AI助手，以 {voice} 的口吻回答问题", deepSeekModel);
    }

    /**
     * 可手动使用 OpenAiChatClient（通过OpenRouter网关访问OpenAI模型）
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        return buildChatClient(openAiChatModel, "OpenAI 聊天AI助手，以 {voice} 的口吻回答问题", openAiModel);
    }

    /**
     * 本地OllamaChatClient
     */
    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        return buildChatClient(ollamaChatModel, "本地Ollama AI助手，以 {voice} 的口吻回答问题", ollamaModel);
    }

    /**
     * 统一构建带默认System上下文的 ChatClient
     */
    private ChatClient buildChatClient(ChatModel chatModel, String systemText, String modelName) {
        return ChatClient.builder(chatModel)
                .defaultSystem(s -> s.text(systemText)
                        .metadata("model", modelName))
                .build();
    }

    /**
     * JDBC构建聊天记忆，DeepSeekChatClient
     * 默认日志记录
     * 工具搜索工具，按需动态加载工具
     */
    @Bean
    public ChatClient jdbcMemoryChatClient(DeepSeekChatModel deepSeekChatModel, JdbcChatMemoryRepository jdbcChatMemoryRepository,
                                           ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilderProvider) {
        // 构建jdbcChatMemory
        ChatMemory jdbcChatMemory = MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .build();

        // 构建chatMemoryAdvisor
        MessageChatMemoryAdvisor chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(jdbcChatMemory).build();

        // 构建工具搜索工具
        ToolCallingAdvisor toolSearchAdvisor = Objects.requireNonNull(toolCallingAdvisorBuilderProvider.getIfAvailable()).build();

        return ChatClient.builder(deepSeekChatModel)
                .defaultSystem(s -> s.text("DeepSeek 具有聊天记忆的AI助手，以 {voice} 的口吻回答问题")
                        .metadata("model", deepSeekModel))
                .defaultAdvisors(defaultLoggerAdvisor, chatMemoryAdvisor, toolSearchAdvisor)
                .build();
    }

    /**
     * Bing网络搜索 MCP ChatClient
     * 可替换为 tavily-search-mcp，需申请 API-KEY
     */
    @Bean
    public ChatClient bingChatClient(List<McpSyncClient> mcpSyncClients,
                                     ChatClient.Builder chatClientBuilder) {
        String systemPrompt = "你是一位得力的搜索助手，能够使用 必应bing 搜索引擎进行网络搜索，从而回答实时问题。回答实时问题时，使用 UTC+8 时间";
        return buildMCPChatClient(mcpSyncClients, chatClientBuilder, systemPrompt);
    }

    /**
     * MySQL数据库操作 MCP ChatClient
     */
    @Bean
    public ChatClient mysqlChatClient(List<McpSyncClient> mcpSyncClients,
                                      ChatClient.Builder chatClientBuilder) {
        String systemPrompt = String.format("""
                        你是一位专业的 MySQL 数据库助手，通过 MCP MySQL Server 操作数据库。
                        连接信息：host=%s, user=%s, password=%s, database=%s。
                        请严格按以下步骤执行：
                        1. 第一步必须先调用 connect_db 工具建立连接，参数依次传入上述 host、user、password、database；
                        2. 连接成功后，如需了解表结构，先调用 list_tables 查看所有表，再调用 describe_table 查看目标表结构；
                        3. 查询(SELECT)用 query 工具，插入/更新/删除用 execute 工具；
                        4. 每次回答都要基于工具返回的真实数据，不要凭空编造。
                        """,
                mysqlHost, mysqlUser, mysqlPassword, mysqlDatabase);
        return buildMCPChatClient(mcpSyncClients, chatClientBuilder, systemPrompt);
    }

    /**
     * Http上下文信息回显 MCP ChatClient
     */
    @Bean
    public ChatClient httpContextChatClient(List<McpSyncClient> mcpSyncClients,
                                            ChatClient.Builder chatClientBuilder) {
        String systemPrompt = "你是一位得力的 HTTP 上下文助手，你需要调用 inspect_http_context MCP工具获取 HTTP 上下文信息，从而回答问题。不要从其他地方获取信息。";
        return buildMCPChatClient(mcpSyncClients, chatClientBuilder, systemPrompt);
    }

    /**
     * Pexels图片搜索 MCP ChatClient
     */
    @Bean
    public ChatClient pexelsChatClient(List<McpSyncClient> mcpSyncClients,
                                       ChatClient.Builder chatClientBuilder) {
        String systemPrompt = "你是一位得力的图片搜索助手，能够使用 image_search MCP工具搜索图片，从而回答问题。";
        return buildMCPChatClient(mcpSyncClients, chatClientBuilder, systemPrompt);
    }

    /**
     * 支付失败原因智能分析 MCP ChatClient
     */
    @Bean
    public ChatClient paymentAnalysisChatClient(List<McpSyncClient> mcpSyncClients,
                                                ChatClient.Builder chatClientBuilder) {
        String systemPrompt = """
                 你是一位专业的支付系统异常分析助手，当用户询问支付异常原因时：
                 - 你必须通过 MCP 工具获取事实
                 - 只能基于 MCP 工具返回的数据进行判断
                 - 禁止基于常识或猜测回答
                
                 工具说明：
                 - PaymentOrderTools：查询支付订单事实
                 - PaymentChannelTools：查询支付渠道状态
                 - MerchantConfigTools：查询商户支付配置状态
                 - RiskRuleTools：查询是否命中风控规则
                 - PaymentSuggestionTools：在完成分析后生成支付异常处理建议
                
                 分析流程建议（非强制）：
                 1. 先确认支付订单状态
                 2. 再判断渠道、商户、风控因素
                 3. 最后再生成处理建议
                """;

        String mysqlPrompt = String.format("""
                        可以通过 MCP MySQL Server 操作数据库。
                        连接信息：host=%s, user=%s, password=%s, database=%s。
                        请严格按以下步骤执行：
                        1. 第一步必须先调用 connect_db 工具建立连接，参数依次传入上述 host、user、password、database；
                        2. 连接成功后，如需了解表结构，先调用 list_tables 查看所有表，再调用 describe_table 查看目标表结构；
                        3. 查询(SELECT)用 query 工具，插入/更新/删除用 execute 工具；
                        4. 每次回答都要基于工具返回的真实数据，不要凭空编造。
                        """,
                mysqlHost, mysqlUser, mysqlPassword, mysqlDatabase);
        return buildMCPChatClient(mcpSyncClients, chatClientBuilder, systemPrompt + mysqlPrompt);
    }

    /**
     * 构建 MCP ChatClient
     */
    private ChatClient buildMCPChatClient(List<McpSyncClient> mcpSyncClients,
                                          ChatClient.Builder chatClientBuilder,
                                          String systemPrompt) {
        ToolCallbackProvider toolCallbackProvider = SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClients).build();
        return chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultTools(toolCallbackProvider)
                .build();
    }
}
