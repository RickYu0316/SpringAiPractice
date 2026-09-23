package cn.javastack.ai.controller;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Agent 智能体接口", description = "Agent 智能体接口")
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final ResumeEvaluationAgent resumeEvaluationAgent;

    @Operation(summary = "HR智能评估筛选简历")
    @PostMapping(value = "/resumeEvaluate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String resumeEvaluate(@Parameter(description = "简历附件", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(type = "string", format = "binary")))
                                 @RequestParam("resumeFile") MultipartFile resumeFile) {
        log.info("HR智能评估筛选简历，执行...");
        String resumeContent = DocumentParseUtils.extractContent(resumeFile);
        return resumeEvaluationAgent.evaluate(resumeContent);
    }
}
