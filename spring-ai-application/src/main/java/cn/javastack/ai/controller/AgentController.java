package cn.javastack.ai.controller;

import cn.hutool.core.util.StrUtil;
import cn.javastack.ai.agent.CodeReviewAgent;
import cn.javastack.ai.agent.OnlineCustomerAgent;
import cn.javastack.ai.agent.ResumeEvaluationAgent;
import cn.javastack.ai.utils.DocumentParseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Agent 智能体接口", description = "Agent 智能体接口")
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final ResumeEvaluationAgent resumeEvaluationAgent;

    private final CodeReviewAgent codeReviewAgent;

    private final OnlineCustomerAgent onlineCustomerAgent;

    @Operation(summary = "HR智能评估筛选简历")
    @PostMapping(value = "/resumeEvaluate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String resumeEvaluate(@Parameter(description = "简历附件", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(type = "string", format = "binary")))
                                 @RequestParam("resumeFile") MultipartFile resumeFile) {
        log.info("HR智能评估筛选简历，执行...");
        String resumeContent = DocumentParseUtils.extractContent(resumeFile);
        return resumeEvaluationAgent.evaluate(resumeContent);
    }

    @Operation(summary = "Java代码质量审查")
    @PostMapping(value = "/codeReview", consumes = MediaType.TEXT_PLAIN_VALUE)
    public String codeReview(@Parameter(description = "待审查的Java代码") @RequestBody String codeContent) {
        if (StrUtil.isBlank(codeContent)) {
            throw new IllegalArgumentException("代码内容不能为空");
        }
        log.info("Java代码质量审查，执行...");
        return codeReviewAgent.review(codeContent);
    }

    @Operation(summary = "智能在线客服")
    @PostMapping(value = "/onlineCustomer", consumes = MediaType.TEXT_PLAIN_VALUE)
    public String onlineCustomer(@Parameter(description = "用户咨询问题") @RequestBody String message) {
        if (StrUtil.isBlank(message)) {
            throw new IllegalArgumentException("咨询内容不能为空");
        }
        log.info("智能在线客服，执行...");
        return onlineCustomerAgent.route(message);
    }
}
