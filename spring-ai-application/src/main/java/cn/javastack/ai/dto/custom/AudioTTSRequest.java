package cn.javastack.ai.dto.custom;

import lombok.Data;

/**
 * OpenAi 文字转语音请求参数
 */
@Data
public class AudioTTSRequest {

    private String message;

    private String model;

    // 音色：eve/ara/rex/sal/leo（x-ai/grok-voice-tts-1.0）
    private String voice = "eve";

    // 语速
    private Double speed = 1.0d;

    // 格式
    private String responseFormat = "mp3";
}
