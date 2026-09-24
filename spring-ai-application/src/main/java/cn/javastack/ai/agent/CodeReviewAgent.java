package cn.javastack.ai.agent;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Java代码质量审查
 * Parallelization Workflow（并行工作流）
 */
@Slf4j
@Component
public class CodeReviewAgent {

    @Resource(name = "deepSeekChatClient")
    private ChatClient deepSeekChatClient;

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    /**
     * 单维度审查超时时间（秒）
     */
    private static final long TIMEOUT_SECONDS = 180;

    /**
     * 代码审查维度
     */
    private static final List<String> REVIEW_INPUTS = List.of(
            """
                    审查维度：安全风险审查
                    
                    重点关注：
                    - 是否存在 SQL 注入风险；
                    - 是否存在命令注入风险；
                    - 是否存在敏感信息泄露；
                    - 是否存在越权访问风险；
                    - 是否存在不安全的反序列化；
                    - 是否存在输入参数未校验问题。
                    """,

            """
                    审查维度：性能问题审查
                    
                    重点关注：
                    - 是否存在循环中频繁访问数据库；
                    - 是否存在不必要的对象创建；
                    - 是否存在低效集合操作；
                    - 是否存在重复计算；
                    - 是否存在阻塞调用；
                    - 是否存在可能导致内存压力的问题。
                    """,

            """
                    审查维度：代码规范审查
                    
                    重点关注：
                    - 命名是否清晰；
                    - 方法职责是否单一；
                    - 代码格式是否规范；
                    - 是否存在魔法值；
                    - 是否存在重复代码；
                    - 是否符合常见 Java 编码习惯。
                    """,

            """
                    审查维度：异常处理审查
                    
                    重点关注：
                    - 是否吞掉异常；
                    - 是否只打印异常但没有处理；
                    - 是否存在空指针风险；
                    - 是否存在资源未关闭问题；
                    - 是否存在错误的异常类型；
                    - 是否缺少必要的边界判断。
                    """,

            """
                    审查维度：可维护性审查
                    
                    重点关注：
                    - 代码是否容易理解；
                    - 逻辑是否过于复杂；
                    - 是否存在过长方法；
                    - 是否存在高耦合；
                    - 是否方便扩展；
                    - 是否方便单元测试。
                    """
    );

    /**
     * 执行 Parallelization Workflow，各维度并行审查后汇总
     */
    public String review(String codeContent) {
        // 并行审查各维度
        List<String> parallelResults = parallel(codeContent);

        // 汇总并行审查结果
        return summarize(parallelResults);
    }

    /**
     * 各维度并行执行。带超时降级，结果按维度顺序返回
     */
    private List<String> parallel(String codeContent) {
        // 每个维度提交一个异步任务
        List<CompletableFuture<String>> futures = REVIEW_INPUTS.stream()
                .map(input -> CompletableFuture.supplyAsync(() -> reviewDimension(codeContent, input), taskExecutor))
                .toList();

        // 按维度顺序收集。超时或异常进行维度降级，不阻塞汇总
        List<String> results = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.add(futures.get(i).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                log.error("审查维度 {} 超时，降级处理", i + 1);
                results.add("该维度审查超时，未获取到结果。");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("审查维度 {} 被中断，降级处理", i + 1);
                results.add("该维度审查被中断，未获取到结果。");
            } catch (ExecutionException e) {
                log.error("审查维度 {} 执行失败：{}", i + 1, e.getMessage(), e);
                results.add("该维度审查执行失败，未获取到结果。");
            }
        }
        return results;
    }

    /**
     * 单维度审查：system 传审查角色与维度，user 传代码
     */
    private String reviewDimension(String codeContent, String dimension) {
        return deepSeekChatClient.prompt()
                .system("""
                        你是一名资深的 Java 代码审查专家。
                        
                        请只从用户输入的【审查维度】审查这段 Java 代码。
                        
                        要求：
                        - 只分析当前输入指定的审查维度，不要分析其他维度；
                        - 问题要具体，不要泛泛而谈；
                        - 如果没有发现明显问题，请明确说明；
                        - 输出 Markdown 格式；
                        - 输出内容要包含：问题说明、严重程度、修改建议。
                        
                        """ + dimension)
                .user(codeContent)
                .call()
                .content();
    }

    /**
     * 汇总多个并行审查结果
     */
    private String summarize(List<String> parallelResults) {
        // 拼接多个并行审查结果
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < parallelResults.size(); i++) {
            context.append("## 审查任务 ").append(i + 1).append("\n\n");
            context.append("### 审查维度\n").append(REVIEW_INPUTS.get(i)).append("\n\n");
            context.append("### 审查结果\n").append(parallelResults.get(i)).append("\n\n");
        }

        return deepSeekChatClient.prompt()
                .system("""
                        你是一名资深的 Java 技术专家。
                        
                        下面是一段 Java 代码的多个并行审查结果。
                        请你根据这些结果，生成最终代码质量审查报告。
                        
                        要求：
                        - 不要重新审查代码，只基于已有并行审查结果进行汇总；
                        - 合并重复问题；
                        - 按严重程度排序；
                        - 给出明确修改建议；
                        - 输出 Markdown 格式。
                        
                        最终报告格式：
                        
                        # 代码质量审查报告
                        
                        | 维度 | 问题摘要 | 严重程度 | 修改建议 |
                        |:--|:--|:--|:--|
                        | 安全风险 | xxx | 高/中/低 | xxx |
                        | 性能问题 | xxx | 高/中/低 | xxx |
                        | 代码规范 | xxx | 高/中/低 | xxx |
                        | 异常处理 | xxx | 高/中/低 | xxx |
                        | 可维护性 | xxx | 高/中/低 | xxx |
                        
                        ## 总体结论
                        
                        用 100 字左右总结这段代码的整体质量。
                        """)
                .user(context.toString())
                .call()
                .content();
    }
}
