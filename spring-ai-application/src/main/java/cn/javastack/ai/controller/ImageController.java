package cn.javastack.ai.controller;

import cn.hutool.core.util.StrUtil;
import cn.javastack.ai.common.GeneratedMediaCache;
import cn.javastack.ai.client.OpenRouterClient;
import cn.javastack.ai.dto.custom.ImageGenerationRequest;
import cn.javastack.ai.utils.MediaTypeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.metadata.OpenAiImageResponseMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图像 Controller
 */
@Tag(name = "Image 图像接口", description = "图像接口")
@Slf4j
@RestController
@RequestMapping("/image")
@RequiredArgsConstructor
public class ImageController {

    private final OpenRouterClient openRouterClient;

    private final GeneratedMediaCache generatedMediaCache;

    @Resource(name = "openAiChatClient")
    private ChatClient openAiChatClient;

    private final OpenAiImageModel openaiImageModel;

    @Value("${spring.ai.openai.image.model}")
    private String openAiDefaultImageModel;

    @Operation(summary = "OpenRouter图像生成")
    @GetMapping("/openRouterGenerate")
    public Map<String, Object> openRouterGenerate(
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

    @Operation(summary = "本地图像理解")
    @GetMapping("/localImageUnderstand")
    public String localImageUnderstand(@Parameter(description = "输入问题") @RequestParam(value = "message") String message,
                                       @Parameter(description = "本地图像地址") @RequestParam(value = "imageUrl", required = false) String imageUrl) {
        Media media;
        if (StringUtils.hasText(imageUrl)) {
            // 获取本地图片
            media = new Media(MimeTypeUtils.IMAGE_JPEG, new FileSystemResource(imageUrl));
        } else {
            log.info("本地图像地址为空，使用默认 ClasPath 路径下的图片");
            // 获取 ClasPath 路径下的图片
            var imageResource = new ClassPathResource("/static/images/nature.jpg");
            media = new Media(MimeTypeUtils.IMAGE_JPEG, imageResource);
        }

        var userMessage = UserMessage.builder()
                .text(message)
                .media(media)
                .build();
        return openAiChatClient.prompt(new Prompt(userMessage))
                .call()
                .content();
    }

    @Operation(summary = "远程网络图像理解")
    @GetMapping("/remoteImageUnderstand")
    public String remoteImageUnderstand(@Parameter(description = "输入问题") @RequestParam(value = "message") String message,
                                        @Parameter(description = "远程网络图像地址") @RequestParam(value = "imageUrl", required = false) String imageUrl) {
        // 获取远程网络图片
        if (!StringUtils.hasText(imageUrl)) {
            log.info("远程网络图像地址为空，使用默认网络图像地址");
            imageUrl = "https://fastly.picsum.photos/id/941/800/600.jpg?hmac=HFrLROK2veDlG391SOPw8ckb2dDyBLwL56Up4iJqhmQ";
        }
        Media media = new Media(MimeTypeUtils.IMAGE_JPEG, URI.create(imageUrl));

        var userMessage = UserMessage.builder()
                .text(message)
                .media(media)
                .build();
        return openAiChatClient.prompt(new Prompt(userMessage))
                .call()
                .content();
    }

    // SpringAI OpenAiImageModel 调用 OpenRouter 图像路由
    @Operation(summary = "OpenAi图像生成")
    @GetMapping("/openAiGeneration")
    public Map<String, Object> openAiGeneration(ImageGenerationRequest request) {
        log.info("调用 OpenAi 图像生成，参数：{}", request);

        // model为空时回退到 spring.ai.openai.image.model
        String model = StrUtil.isBlank(request.getModel()) ? openAiDefaultImageModel : request.getModel();
        OpenAiImageOptions openAiImageOptions = buildImagesOptions(request, model);
        ImagePrompt imagePrompt = new ImagePrompt(request.getMessage(), openAiImageOptions);
        ImageResponse response = openaiImageModel.call(imagePrompt);

        Map<String, Object> result = new HashMap<>();
        result.put("created", response.getMetadata() instanceof OpenAiImageResponseMetadata metadata ? metadata.getCreated() : null);
        result.put("count", response.getResults().size());
        result.put("model", model);
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        List<String> urls = new ArrayList<>();
        for (ImageGeneration item : response.getResults()) {
            Image output = item.getOutput();
            // 优先返回远程URL
            if (StrUtil.isNotBlank(output.getUrl())) {
                urls.add(output.getUrl());
                continue;
            }
            // 将base64内容存入缓存，返回可下载的本地地址
            String b64 = output.getB64Json();
            if (StrUtil.isNotBlank(b64)) {
                byte[] data = Base64.getDecoder().decode(b64);
                urls.add(base + "/image/download/" + generatedMediaCache.store(data, "image/png"));
            }
        }
        result.put("urls", urls);
        return result;
    }

    /**
     * 构建 OpenAi 图像参数
     */
    private static OpenAiImageOptions buildImagesOptions(ImageGenerationRequest request, String model) {
        OpenAiImageOptions.Builder builder = OpenAiImageOptions.builder();

        if (StrUtil.isNotBlank(model)) {
            builder.model(model);
        }
        if (request.getN() != null) {
            builder.n(request.getN());
        }
        if (StrUtil.isNotBlank(request.getSize())) {
            builder.size(request.getSize());
        }
        if (StrUtil.isNotBlank(request.getQuality())) {
            builder.quality(request.getQuality());
        }
        if (StrUtil.isNotBlank(request.getStyle())) {
            builder.style(request.getStyle());
        }
        return builder.build();
    }
}
