package cn.javastack.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 音频 Controller
 * TTS（文本转语音）与STT（语音转文字）均通过OpenRouter网关调用OpenAI音频模型
 */
@Tag(name = "Audio 音频接口", description = "语音合成与语音识别接口")
@Slf4j
@RestController
@RequestMapping("/audio")
public class AudioController {

    @Resource(name = "openAiSdkAudioSpeechModel")
    private TextToSpeechModel audioSpeechModel;

    @Resource(name = "openAiSdkAudioTranscriptionModel")
    private TranscriptionModel audioTranscriptionModel;

    @Operation(summary = "文本转语音 TTS")
    @GetMapping(value = "/speech", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] speech(
            @Parameter(description = "要转语音的文本") @RequestParam("text") String text,
            @Parameter(description = "音色：alloy") @RequestParam(value = "voice", required = false, defaultValue = "alloy") String voice) {
        log.info("调用TTS，text：{}，voice：{}", text, voice);

        // 以yaml默认options为准（含配置的model），避免model被builder默认值覆盖
        OpenAiAudioSpeechOptions defaultOptions = (OpenAiAudioSpeechOptions) audioSpeechModel.getOptions();
        TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, OpenAiAudioSpeechOptions.builder()
                .from(defaultOptions)
                .voice(voice)
                .build());
        TextToSpeechResponse response = audioSpeechModel.call(prompt);
        return response.getResult().getOutput();
    }

    @Operation(summary = "语音转文字 STT")
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String transcribe(
            @Parameter(description = "音频文件（mp3/wav/flac等）") @RequestParam("file") MultipartFile file) {
        log.info("调用STT，文件名：{}，大小：{} bytes", file.getOriginalFilename(), file.getSize());

        try {
            org.springframework.core.io.Resource audioResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            return audioTranscriptionModel.transcribe(audioResource);
        } catch (Exception e) {
            log.error("调用STT接口失败：{}", e.getMessage(), e);
            throw new RuntimeException("语音识别失败：" + e.getMessage(), e);
        }
    }
}
