package cn.javastack.ai.controller;

import cn.javastack.ai.common.GeneratedMediaCache;
import cn.javastack.ai.client.OpenRouterClient;
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

    private final GeneratedMediaCache generatedMediaCache;

    @Operation(summary = "图像生成")
    @GetMapping("/generate")
    public Map<String, Object> generate(
            @Parameter(description = "图像描述") @RequestParam("prompt") String prompt,
            @Parameter(description = "图像模型") @RequestParam(value = "model", required = false) String model,
            @Parameter(description = "尺寸：支持 2K/4K 或像素值，像素值不低于1920x1920") @RequestParam(value = "size", required = false) String size) {
        return openRouterClient.generateImage(prompt, model, size);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable("id") String id) {
        GeneratedMediaCache.CachedMedia image = generatedMediaCache.get(id);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"image-" + image.id() + "." + MediaTypeUtils.fileExtension(image.mediaType()) + "\"")
                .body(image.data());
    }
}
