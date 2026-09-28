package cn.javastack.ai.agent;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 商户入网智能预审
 * Orchestrator-Workers（编排者-工作者模式）
 * 流程：Orchestrator 拆解任务 → 多个 Worker 并行专项预审 → Synthesizer 汇总报告
 */
@Slf4j
@Component
public class MerchantPreAuditAgent {

    @Resource(name = "deepSeekChatClient")
    private ChatClient deepSeekChatClient;

    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    /**
     * 单 Worker 预审超时时间（秒）
     */
    private static final long TIMEOUT_SECONDS = 180;

    /**
     * Orchestrator Prompt：拆解预审任务
     */
    private static final String ORCHESTRATOR_PROMPT = """
            你是支付公司的商户接入预审负责人。
            
            请分析下面这份商户入网申请资料，并拆解出 3-20 个需要 Worker 分别执行的预审任务。
            
            你需要重点判断：
            - 这个商户属于什么行业；
            - 是否可能涉及高风险行业；
            - 是否需要关注业务真实性；
            - 是否需要关注合规资质；
            - 是否需要关注交易和结算风险；
            - 是否需要关注技术接入复杂度；
            - 是否需要关注运营服务成本。
            
            请严格按照下面 JSON 格式返回，不要输出 JSON 之外的内容：
            
            \\{
              "analysis": "你对该商户入网申请的整体理解，以及为什么要拆解这些预审任务。",
              "tasks": [
                \\{
                  "type": "business",
                  "description": "从业务真实性和商业价值角度进行预审"
                \\},
                \\{
                  "type": "risk",
                  "description": "从欺诈、套现、退款、投诉、拒付等风险角度进行预审"
                \\}
              ]
            \\}
            """;

    /**
     * Worker Prompt：专项预审
     */
    private static final String WORKER_PROMPT = """
            你是支付公司商户接入预审团队中的专业 Worker。
            
            请根据分配给你的任务，对商户入网申请进行专项预审，只分析当前任务对应的角度。
            
            输出要求：
            - 不要泛泛而谈；
            - 风险判断要具体；
            - 如果信息不足，要明确指出需要补充哪些材料；
            - 不要直接给出最终是否接入结论；
            - 输出 Markdown 格式。
            
            输出格式：
            
            ## 预审角度
            
            ## 主要发现
            
            ## 风险点
            
            ## 需要补充的材料
            
            ## 专项建议
            """;

    /**
     * Synthesizer Prompt：汇总生成预审报告
     */
    private static final String SYNTHESIZER_PROMPT = """
            你是支付公司的商户接入预审负责人。
            
            下面是 Orchestrator 对商户入网申请的分析，以及多个 Worker 的专项预审结果。
            请你综合这些信息，生成一份最终的商户入网智能预审报告。
            
            要求：
            - 不要编造商户资料中不存在的信息；
            - 如果资料不足，要明确写出需要补充的材料；
            - 预审结论只能是“建议进入人工审核”“建议补充材料后再审核”“暂不建议接入”三种之一；
            - 风险等级只能是“低”“中”“高”三种之一；
            - 输出 Markdown 格式。
            
            最终报告格式：
            
            # 商户入网智能预审报告
            
            | 项目 | 结果 |
            |:--|:--|
            | 商户名称 | xxx |
            | 行业类型 | xxx |
            | 接入产品 | xxx |
            | 预计交易规模 | xxx |
            | 综合风险等级 | 低/中/高 |
            | 预审结论 | 建议进入人工审核/建议补充材料后再审核/暂不建议接入 |
            
            ## 主要风险点
            
            ## 专项预审摘要
            
            ## 需要补充的材料
            
            ## 人工复审建议
            """;

    /**
     * Orchestrator 拆解失败时的兜底任务，保证流程完整
     */
    private static final List<Task> DEFAULT_TASKS = List.of(new Task("business", "从业务真实性、商业价值、行业与合规资质角度进行综合预审"));

    /**
     * Orchestrator-Workers
     * 执行商户入网智能预审
     */
    public String preAudit(String application) {
        // Orchestrator 拆解预审任务
        OrchestratorResponse orchestratorResponse = deepSeekChatClient.prompt()
                .system(ORCHESTRATOR_PROMPT)
                .user(application)
                .call()
                .entity(OrchestratorResponse.class);

        if (Objects.isNull(orchestratorResponse) || Objects.isNull(orchestratorResponse.tasks()) || orchestratorResponse.tasks().isEmpty()) {
            log.warn("Orchestrator 未拆解出有效任务，使用兜底任务");
            orchestratorResponse = new OrchestratorResponse("未从商户资料中识别出明确的预审拆分，使用兜底综合预审。", DEFAULT_TASKS);
        }
        log.info("Orchestrator 拆解预审任务：{} 个", orchestratorResponse.tasks().size());

        // 多个 Worker 并行专项预审
        List<String> workerResponses = runWorkers(application, orchestratorResponse.tasks());

        // Synthesizer 汇总生成最终报告
        return synthesize(application, orchestratorResponse, workerResponses);
    }

    /**
     * 并行执行专项预审。超时降级，结果按任务顺序返回
     */
    private List<String> runWorkers(String application, List<Task> tasks) {
        List<CompletableFuture<String>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> audit(task, application), taskExecutor))
                .toList();

        List<String> results = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.add(futures.get(i).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                log.error("预审任务 {} 超时，降级处理", i + 1);
                results.add("该预审任务超时，未获取到结果。");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("预审任务 {} 被中断，降级处理", i + 1);
                results.add("该预审任务被中断，未获取到结果。");
            } catch (ExecutionException e) {
                log.error("预审任务 {} 执行失败：{}", i + 1, e.getMessage(), e);
                results.add("该预审任务执行失败，未获取到结果。");
            }
        }
        return results;
    }

    /**
     * 单 Worker 专项预审
     */
    private String audit(Task task, String application) {
        return deepSeekChatClient.prompt()
                .system(WORKER_PROMPT)
                .user(user -> user.text("""
                                原始商户申请：
                                {original_application}
                                
                                当前任务类型：
                                {task_type}
                                
                                当前任务说明：
                                {task_description}
                                """)
                        .param("original_application", application)
                        .param("task_type", task.type())
                        .param("task_description", task.description()))
                .call()
                .content();
    }

    /**
     * Synthesizer 汇总 Orchestrator 预审任务分析和各 Worker 预审结果，生成最终报告
     */
    private String synthesize(String application,
                              OrchestratorResponse orchestratorResponse,
                              List<String> workerResponses) {
        // 拼接各任务说明与预审结果，作为汇总上下文
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < workerResponses.size(); i++) {
            Task task = orchestratorResponse.tasks().get(i);

            context.append("## Worker ")
                    .append(i + 1)
                    .append("：")
                    .append(task.type())
                    .append("\n\n");

            context.append("### 任务说明\n")
                    .append(task.description())
                    .append("\n\n");

            context.append("### Worker 输出\n")
                    .append(workerResponses.get(i))
                    .append("\n\n");
        }

        return deepSeekChatClient.prompt()
                .system(SYNTHESIZER_PROMPT)
                .user(user -> user.text("""
                                原始商户申请：
                                {original_application}
                                
                                Orchestrator 预审任务分析：
                                {analysis}
                                
                                Worker 预审结果：
                                {worker_responses}
                                """)
                        .param("original_application", application)
                        .param("analysis", orchestratorResponse.analysis())
                        .param("worker_responses", context.toString()))
                .call()
                .content();
    }

    /**
     * Orchestrator 具体的子任务
     */
    public record Task(String type, String description) {
    }

    /**
     * Orchestrator 返回结果
     */
    public record OrchestratorResponse(String analysis, List<Task> tasks) {
    }
}
