package cn.javastack.ai.controller;

import cn.javastack.ai.client.OpenRouterClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 图像生成 Controller
 */
@Tag(name = "Image 图像生成接口", description = "图像生成接口")
@RestController
@RequestMapping("/image")
@RequiredArgsConstructor
public class ImageController {

    private final OpenRouterClient openRouterClient;

    @Operation(summary = "图像生成")
    @GetMapping("/generate")
    public Map<String, Object> generate(
            @Parameter(description = "图像描述") @RequestParam("prompt") String prompt,
            @Parameter(description = "图像模型") @RequestParam(value = "model", required = false) String model,
            @Parameter(description = "尺寸，如 1024x1024") @RequestParam(value = "size", required = false) String size,
            @Parameter(description = "质量：auto/low/medium/high") @RequestParam(value = "quality", required = false) String quality,
            @Parameter(description = "数量 1-10") @RequestParam(value = "n", required = false) Integer n) {
        return openRouterClient.generateImage(prompt, model, size, quality, n);
    }
}
