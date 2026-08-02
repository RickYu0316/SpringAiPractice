package cn.javastack.ai.controller;

import cn.javastack.ai.client.OpenRouterClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 视频 Controller
 */
@Tag(name = "Video 视频接口", description = "视频生成与视频理解接口")
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
public class VideoController {

    private final OpenRouterClient openRouterClient;

    @Operation(summary = "视频生成异步任务")
    @PostMapping("/generate")
    public Map<String, Object> generate(
            @Parameter(description = "视频描述") @RequestParam("prompt") String prompt,
            @Parameter(description = "视频模型") @RequestParam(value = "model", required = false) String model,
            @Parameter(description = "时长（秒）") @RequestParam(value = "duration", required = false) Integer duration,
            @Parameter(description = "分辨率：480p/720p/1080p") @RequestParam(value = "resolution", required = false) String resolution,
            @Parameter(description = "宽高比：16:9/9:16/1:1") @RequestParam(value = "aspectRatio", required = false) String aspectRatio) {
        return openRouterClient.generateVideo(prompt, model, duration, resolution, aspectRatio);
    }

    @Operation(summary = "查询视频生成任务状态")
    @GetMapping("/status/{jobId}")
    public Map<String, Object> status(
            @Parameter(description = "任务ID") @PathVariable("jobId") String jobId) {
        return openRouterClient.getVideoStatus(jobId);
    }

    @Operation(summary = "视频理解（多模态）")
    @PostMapping("/understand")
    public String understand(
            @Parameter(description = "视频相关问题") @RequestParam("message") String message,
            @Parameter(description = "视频URL") @RequestParam("videoUrl") String videoUrl,
            @Parameter(description = "视频模型") @RequestParam(value = "model", required = false) String model) {
        return openRouterClient.understandVideo(message, videoUrl, model);
    }
}
