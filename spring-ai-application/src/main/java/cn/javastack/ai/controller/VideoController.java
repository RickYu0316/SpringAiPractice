package cn.javastack.ai.controller;

import cn.javastack.ai.common.GeneratedMediaCache;
import cn.javastack.ai.service.VideoGenerationService;
import cn.javastack.ai.utils.MediaTypeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    private final VideoGenerationService videoGenerationService;

    private final GeneratedMediaCache generatedMediaCache;

    /**
    * 异步任务，返回taskId后轮询状态
    */
    @Operation(summary = "视频生成（异步）")
    @PostMapping("/generate")
    public Map<String, Object> generate(
            @Parameter(description = "视频描述") @RequestParam("prompt") String prompt,
            @Parameter(description = "视频模型") @RequestParam(value = "model", required = false) String model,
            @Parameter(description = "时长（秒）：4-15") @RequestParam(value = "duration", required = false) Integer duration,
            @Parameter(description = "分辨率：480p/720p/1080p/4K") @RequestParam(value = "resolution", required = false) String resolution,
            @Parameter(description = "宽高比：1:1/3:4/9:16/4:3/16:9/21:9/9:21") @RequestParam(value = "aspectRatio", required = false) String aspectRatio) {
        return videoGenerationService.generate(prompt, model, duration, resolution, aspectRatio);
    }

    @GetMapping("/status/{taskId}")
    public Map<String, Object> status(@PathVariable("taskId") String taskId) {
        return videoGenerationService.getStatus(taskId);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable("id") String id) {
        GeneratedMediaCache.CachedMedia video = generatedMediaCache.get(id);
        if (video == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(video.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"video-" + video.id() + "." + MediaTypeUtils.fileExtension(video.mediaType()) + "\"")
                .body(video.data());
    }

    @Operation(summary = "视频理解")
    @PostMapping("/understand")
    public String understand(
            @Parameter(description = "视频相关问题") @RequestParam("message") String message,
            @Parameter(description = "视频URL（公网视频直链）") @RequestParam("videoUrl") String videoUrl,
            @Parameter(description = "视频模型") @RequestParam(value = "model", required = false) String model) {
        return videoGenerationService.understandVideo(message, videoUrl, model);
    }
}
