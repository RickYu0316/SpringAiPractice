package cn.javastack.ai.controller;

import cn.javastack.ai.common.GeneratedMediaCache;
import cn.javastack.ai.utils.MediaTypeUtils;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * 音频 Controller
 * TTS（文本转语音）与STT（语音转文字）均通过OpenRouter网关调用OpenAI音频模型
 */
@Tag(name = "Audio 音频接口", description = "语音合成与语音识别接口")
@Slf4j
@RestController
@RequestMapping("/audio")
public class AudioController {

    private static final String AUDIO_MEDIA_TYPE = "audio/mpeg";

    @Resource(name = "openAiSdkAudioSpeechModel")
    private TextToSpeechModel audioSpeechModel;

    @Resource(name = "openAiSdkAudioTranscriptionModel")
    private TranscriptionModel audioTranscriptionModel;

    @Resource
    private GeneratedMediaCache generatedMediaCache;

    /*
    * 语音生成技巧：
    * 选择合适的音色
    * 手动加停顿：用[pause]标签在句读、押韵处控制节奏
    * 调整speed语速
    * */
    @Operation(summary = "文本转语音 TTS")
    @GetMapping("/speech")
    public Map<String, Object> speech(
            @Parameter(description = "要转语音的文本") @RequestParam("text") String text,
            @Parameter(description = "音色：eve/ara/rex/sal/leo（x-ai/grok-voice-tts-1.0）") @RequestParam(value = "voice", required = false, defaultValue = "eve") String voice) {
        log.info("调用TTS，text：{}，voice：{}", text, voice);

        // 以yaml中配置的options为准，避免model被builder默认值覆盖
        OpenAiAudioSpeechOptions yamlOptions = (OpenAiAudioSpeechOptions) audioSpeechModel.getOptions();
        TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, OpenAiAudioSpeechOptions.builder()
                .from(yamlOptions)
                .voice(voice)
                .build());
        TextToSpeechResponse response = audioSpeechModel.call(prompt);
        byte[] audio = response.getResult().getOutput();

        String base = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        String id = generatedMediaCache.store(audio, AUDIO_MEDIA_TYPE);
        return Map.of(
                "mediaType", AUDIO_MEDIA_TYPE,
                "urls", List.of(base + "/audio/download/" + id));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable("id") String id) {
        GeneratedMediaCache.CachedMedia media = generatedMediaCache.get(id);
        if (media == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tts-" + media.id() + "." + MediaTypeUtils.fileExtension(media.mediaType()) + "\"")
                .body(media.data());
    }

    // OpenRouter中暂无可用STT模型，暂不使用
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
