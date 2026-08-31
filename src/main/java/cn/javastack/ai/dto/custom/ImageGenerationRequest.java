package cn.javastack.ai.dto.custom;

import lombok.Data;

/**
 * OpenAi 图像生成请求参数
 */
@Data
public class ImageGenerationRequest {

    private String message;

    private String model;

    // 图片数量：seedream 系列即使传 n>1 也只会返回 1 张
    private Integer n = 1;

    // 尺寸；seedream 要求总像素不低于 3,686,400（约 1920x1920）
    private String size;

    // 画质：low, medium, high
    private String quality;

    // 风格：vivid, natural
    private String style;
}
