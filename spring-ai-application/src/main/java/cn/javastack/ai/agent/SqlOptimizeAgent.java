package cn.javastack.ai.agent;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SQL智能优化
 * Evaluator-Optimizer（评估-优化器模式）
 */
@Slf4j
@Component
public class SqlOptimizeAgent {

    @Resource(name = "deepSeekChatClient")
    private ChatClient deepSeekChatClient;

    /**
     * 默认最大优化轮次
     */
    public static final int DEFAULT_MAX_ROUNDS = 3;

    /**
     * Optimizer Prompt：生成或优化 SQL
     */
    private static final String OPTIMIZER_PROMPT = """
            你是一名资深的数据库 SQL 优化专家。
            
            你的任务是根据用户输入的原始SQL、表结构信息、索引信息、业务背景和历史反馈，
            生成一份优化后的 SQL 方案。
            
            你需要重点关注：
            - 是否存在 SELECT *；
            - 是否存在函数导致索引失效；
            - 是否存在隐式类型转换；
            - 是否存在不合理的 JOIN；
            - 是否存在子查询可优化空间；
            - 是否存在排序、分页、分组性能问题；
            - 是否需要补充索引；
            - 优化后 SQL 是否和原始 SQL 保持语义一致。
            
            输出要求：
            - 只输出下面的 JSON；
            - 不要编造不存在的表结构；
            - optimizedSql 中如果有换行，请使用 \\n；
            - 如果缺少表结构或索引信息，要在 risk 中说明。
            
            JSON 格式如下：
            
            {
              "analysis": "对原始 SQL 问题的分析",
              "optimizedSql": "优化后的 SQL",
              "optimizationPoints": "本次优化点说明",
              "indexSuggestions": "索引建议",
              "risk": "潜在风险或需要人工确认的地方"
            }
            """;

    /**
     * Evaluator Prompt：评估 Optimizer SQL优化结果
     */
    private static final String EVALUATOR_PROMPT = """
            你是一名资深的数据库审核专家。
            
            请评估下面这份 SQL 优化结果是否合格。
            
            评估标准：
            - 优化后的 SQL 是否和原始 SQL 语义一致；
            - 不要编造不存在的表结构和索引；
            - 是否避免了明显索引失效问题；
            - 是否减少了不必要字段返回；
            - 是否存在错误改写；
            - 是否存在分页、排序、分组、JOIN 风险；
            - 索引建议是否合理；
            - 是否有必要补充风险说明。
            
            evaluation 字段只能是：
            - PASS：优化结果合格；
            - NEEDS_IMPROVEMENT：存在可改进问题；
            - FAIL：优化结果明显错误或风险较高。
            
            输出要求：
            - 只输出 JSON；
            - feedback 要具体、可执行。
            
            JSON 格式如下：
            
            {
              "evaluation": "PASS",
              "feedback": "评估意见"
            }
            """;

    /**
     * Evaluator-Optimizer
     * 执行 SQL智能优化
     */
    public RefinedResponse optimize(SqlOptimizeRequest request) {
        Assert.hasText(request.sql(), "原始 SQL 不能为空");

        int maxRounds = request.maxRounds() != null && request.maxRounds() > 0
                ? request.maxRounds() : DEFAULT_MAX_ROUNDS;

        // 保存每一轮优化结果，供最终响应返回
        List<Generation> chainOfThought = new ArrayList<>();
        return loop(request, maxRounds, null, chainOfThought, 1);
    }

    /**
     * Evaluator-Optimizer 核心循环
     */
    private RefinedResponse loop(SqlOptimizeRequest request,
                                 int maxRounds,
                                 String context,
                                 List<Generation> chainOfThought,
                                 int round) {
        log.info("开始第 {} 轮 SQL优化", round);

        // Optimizer 生成或优化SQL
        Generation generation = generate(request, context, round);
        chainOfThought.add(generation);

        // Evaluator 评估本轮优化结果
        EvaluationResponse evaluation = evaluate(request, generation, round);

        // 评估通过或达到最大轮次时结束
        boolean passed = evaluation.evaluation() == Evaluation.PASS;
        if (passed || round >= maxRounds) {
            return new RefinedResponse(generation, evaluation, chainOfThought, passed, round);
        }

        // 未通过，携带历史优化SQL与评估反馈继续下一轮
        return loop(request, maxRounds, buildContext(context, generation, evaluation),
                chainOfThought, round + 1);
    }

    /**
     * Optimizer：生成或优化 SQL
     */
    private Generation generate(SqlOptimizeRequest request,
                                String context,
                                int round) {
        Generation generation = deepSeekChatClient.prompt()
                .system(OPTIMIZER_PROMPT)
                .user(user -> user.text("""
                                当前轮次：
                                {round}
                                
                                原始 SQL：
                                {sql}
                                
                                表结构信息：
                                {schema}
                                
                                已有索引信息：
                                {indexes}
                                
                                业务背景：
                                {businessContext}
                                
                                历史优化与评估反馈：
                                {context}
                                """)
                        .param("round", round)
                        .param("sql", request.sql())
                        .param("schema", StrUtil.blankToDefault(request.schema(), "未提供"))
                        .param("indexes", StrUtil.blankToDefault(request.indexes(), "未提供"))
                        .param("businessContext", StrUtil.blankToDefault(request.businessContext(), "未提供"))
                        .param("context", StrUtil.blankToDefault(context, "本轮为首次优化")))
                .call()
                .entity(Generation.class);

        if (Objects.isNull(generation)) {
            throw new IllegalStateException("第 " + round + " 轮 SQL 优化失败");
        }
        return generation;
    }

    /**
     * Evaluator：评估优化结果
     */
    private EvaluationResponse evaluate(SqlOptimizeRequest request,
                                        Generation generation,
                                        int round) {
        EvaluationResponse evaluation = deepSeekChatClient.prompt()
                .system(EVALUATOR_PROMPT)
                .user(user -> user.text("""
                                原始 SQL：
                                {sql}
                                
                                表结构信息：
                                {schema}
                                
                                已有索引信息：
                                {indexes}
                                
                                业务背景：
                                {businessContext}
                                
                                待评估的优化结果：
                                analysis:
                                {analysis}
                                
                                optimizedSql:
                                {optimizedSql}
                                
                                optimizationPoints:
                                {optimizationPoints}
                                
                                indexSuggestions:
                                {indexSuggestions}
                                
                                risk:
                                {risk}
                                """)
                        .param("sql", request.sql())
                        .param("schema", StrUtil.blankToDefault(request.schema(), "未提供"))
                        .param("indexes", StrUtil.blankToDefault(request.indexes(), "未提供"))
                        .param("businessContext", StrUtil.blankToDefault(request.businessContext(), "未提供"))
                        .param("analysis", generation.analysis())
                        .param("optimizedSql", generation.optimizedSql())
                        .param("optimizationPoints", StrUtil.blankToDefault(generation.optimizationPoints(), "未提供"))
                        .param("indexSuggestions", StrUtil.blankToDefault(generation.indexSuggestions(), "未提供"))
                        .param("risk", StrUtil.blankToDefault(generation.risk(), "未提供")))
                .call()
                .entity(EvaluationResponse.class);

        // 评估优化结果失败时降级为待改进，避免整个流程中断
        if (Objects.isNull(evaluation)) {
            log.warn("第 {} 轮评估优化结果失败，降级为待改进", round);
            return new EvaluationResponse(Evaluation.NEEDS_IMPROVEMENT, "评估优化结果失败，请人工复核本轮优化结果。");
        }
        return evaluation;
    }

    /**
     * 累积每轮优化SQL与评估反馈，作为下一轮上下文
     */
    private String buildContext(String prevContext,
                                Generation generation,
                                EvaluationResponse evaluation) {
        StringBuilder newContext = new StringBuilder();
        if (StrUtil.isNotBlank(prevContext)) {
            newContext.append(prevContext).append("\n\n");
        }
        newContext.append("本轮优化 SQL：\n")
                .append(StrUtil.blankToDefault(generation.optimizedSql(), "未提供"))
                .append("\n\n本轮评估反馈：\n")
                .append(evaluation.feedback());
        return newContext.toString();
    }

    /**
     * SQL 优化请求
     */
    public record SqlOptimizeRequest(
            String sql,
            String schema,
            String indexes,
            String businessContext,
            Integer maxRounds) {
    }

    /**
     * Optimizer sql优化结果
     */
    public record Generation(
            String analysis,
            String optimizedSql,
            String optimizationPoints,
            String indexSuggestions,
            String risk) {
    }

    /**
     * Evaluator 评估结果
     */
    public record EvaluationResponse(
            Evaluation evaluation,
            String feedback) {
    }

    /**
     * 评估状态
     */
    public enum Evaluation {
        PASS,
        NEEDS_IMPROVEMENT,
        FAIL;

        /**
         * LLM 输出大小写不固定，反序列化时做宽松匹配，未知值降级为待改进
         */
        @JsonCreator
        public static Evaluation fromValue(String value) {
            if (StrUtil.isBlank(value)) {
                return NEEDS_IMPROVEMENT;
            }
            for (Evaluation e : values()) {
                if (e.name().equalsIgnoreCase(value.trim())) {
                    return e;
                }
            }
            return NEEDS_IMPROVEMENT;
        }
    }

    /**
     * 最终优化响应
     */
    public record RefinedResponse(
            Generation finalResult,
            EvaluationResponse finalEvaluation,
            List<Generation> chainOfThought,
            boolean passed,
            int rounds) {
    }
}
