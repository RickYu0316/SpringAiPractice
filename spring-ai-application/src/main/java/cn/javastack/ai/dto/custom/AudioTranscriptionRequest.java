package cn.javastack.ai.dto.custom;

import lombok.Data;

/**
 * OpenAi 语音转文字请求参数
 */
@Data
public class AudioTranscriptionRequest {

    private String audioFilePath;

    private String model;

    // 语言
    private String language = "zh";

    // 采样温度：deepgram/nova-3 仅支持 0~1
    private Float temperature = 0.2f;
}
