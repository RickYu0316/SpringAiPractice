package cn.javastack.ai.agent;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * HR智能评估筛选简历
 * Chain Workflow（串行工作流）
 */
@Slf4j
@Component
public class ResumeEvaluationAgent {

    @Resource(name = "deepSeekChatClient")
    private ChatClient deepSeekChatClient;

    /**
     * 工作流中的 Prompts
     */
    private static final String[] DEFAULT_SYSTEM_PROMPTS = {
            """
                请从简历原文中提取候选人的核心信息。

                输出以下等信息：
                - 候选人姓名
                - 年龄
                - 学历
                - 工作年限
                - 技术栈
                - 项目经验
                - 当前岗位
                - 目标岗位
                """,

            """
                根据简历原文和上一步提取的信息，先简要复述候选人姓名、年龄、学历、当前岗位，再分析候选人的技术能力与项目质量。

                重点分析：
                - 是否离职超过三个月、最近工作的一家公司是否超过一年（可提取最后一份工作的截止时间进行计算，如显示至今或者为空表示在职）
                - 技术深度
                - 技术广度
                - 项目复杂度
                - 是否具备高并发/分布式经验
                - 是否有 AI 相关落地能力
                - 是否存在明显简历包装

                输出格式：
                - 候选人基础信息（姓名、年龄、学历、当前岗位）
                - 技术能力：
                - 项目质量：
                - 风险点：
                - 综合评价：
                """,

            """
                根据简历原文和前面的技术分析结果，先简要复述候选人姓名、年龄、学历，再评估候选人与目标岗位的匹配度，如果无目标岗位，默认为 Java 后端开发工程师。

                匹配等级：
                - 高
                - 中
                - 低

                需要分析：
                - 离职时长
                - 最近一家公司的工作时长
                - 技能匹配情况
                - 工作经验匹配情况
                - 是否满足岗位要求
                - 是否建议进入下一轮

                输出格式：
                - 候选人基础信息（姓名、年龄、学历）
                - 匹配等级：
                - 匹配分析：
                - 是否推荐面试：
                - 推荐原因：
                """,

            """
                根据简历原文和前面的所有分析结果，生成最终 HR 筛选报告。
                前面步骤已包含候选人姓名、年龄、学历等基础信息，最终表格须如实填写，不得出现"未提供"。

                要求：
                - 只输出一张 Markdown 表格；
                - 不要输出表格之外的任何标题、说明、总结、列表或补充段落；
                - 表格内容使用简洁专业的 HR 风格；
                - 核心优势、主要风险点和最终筛选结论都写入表格对应单元格。

                输出格式必须严格如下：
                | 项目 | 结果 |
                |:--|:--|
                | 候选人 | xxx |
                | 年龄 | xxx |
                | 学历 | xxx |
                | 工作年限 | xxx |
                | 在职情况 | xxx |
                | 技术能力 | xxx |
                | 项目质量 | xxx |
                | 匹配等级 | xxx |
                | 核心优势 | xxx |
                | 风险点 | xxx |
                | 是否推荐面试 | xxx |
                | 筛选结论 | xxx |
                """};

    /**
     * 执行 Chain Workflow
     * 串行执行：每一步携带完整上下文（原始简历 + 前面所有步骤的结果）
     */
    public String evaluate(String resumeContent) {
        if (StrUtil.isBlank(resumeContent)) {
            throw new IllegalArgumentException("简历内容不能为空");
        }

        StringBuilder context = new StringBuilder(resumeContent);
        String response = resumeContent;
        int step = 0;

        for (String stepPrompt : DEFAULT_SYSTEM_PROMPTS) {
            response = deepSeekChatClient.prompt()
                    .system(stepPrompt)
                    .user(context.toString())
                    .call()
                    .content();

            if (StrUtil.isBlank(response)) {
                throw new IllegalStateException("第 " + (step + 1) + " 步输出为空，工作流中断");
            }
            log.info("\nSTEP {}:\n{}", step + 1, response);

            context.append("\n\n==================== 第 ").append(step + 1).append(" 步分析结果 ====================\n").append(response);
            step++;
        }

        return response;
    }
}
