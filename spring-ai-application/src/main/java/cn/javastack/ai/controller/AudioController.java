package cn.javastack.ai.controller;

import cn.hutool.core.util.StrUtil;
import cn.javastack.ai.common.GeneratedMediaCache;
import cn.javastack.ai.dto.custom.AudioTTSRequest;
import cn.javastack.ai.dto.custom.AudioTranscriptionRequest;
import cn.javastack.ai.utils.MediaTypeUtils;
import com.openai.models.audio.AudioResponseFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
@RequiredArgsConstructor
public class AudioController {

    private static final String AUDIO_MEDIA_TYPE = "audio/mpeg";

    @Resource(name = "openAiSdkAudioSpeechModel")
    private TextToSpeechModel audioSpeechModel;

    private final OpenAiAudioSpeechModel openAiAudioSpeechModel;

    private final OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel;

    @Value("${spring.ai.openai.audio.speech.model}")
    private String openAiDefaultSpeechModel;

    @Value("${spring.ai.openai.audio.transcription.model}")
    private String openAiDefaultTranscriptionModel;

    @Value("${spring.ai.openai.audio.transcription.language}")
    private String openAiDefaultTranscriptionLanguage;

    @Value("${spring.ai.openai.audio.transcription.temperature}")
    private Float openAiDefaultTranscriptionTemperature;

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

    @Operation(summary = "OpenAi 文本转语音 TTS")
    @GetMapping("/tts")
    public Map<String, Object> tts(AudioTTSRequest audioTTSRequest) {
        log.info("调用 OpenAi 文本转语音 TTS，参数：{}", audioTTSRequest);

        String model = StrUtil.isBlank(audioTTSRequest.getModel()) ? openAiDefaultSpeechModel : audioTTSRequest.getModel();
        TextToSpeechOptions textToSpeechOptions = buildSpeechOptions(audioTTSRequest, model);
        TextToSpeechPrompt prompt = new TextToSpeechPrompt(audioTTSRequest.getMessage(), textToSpeechOptions);
        TextToSpeechResponse response = openAiAudioSpeechModel.call(prompt);
        byte[] audioBytes = response.getResult().getOutput();

        String base = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        String id = generatedMediaCache.store(audioBytes, AUDIO_MEDIA_TYPE);
        return Map.of(
                "mediaType", AUDIO_MEDIA_TYPE,
                "urls", List.of(base + "/audio/download/" + id));
    }

    @Operation(summary = "OpenAi 语音转文字 STT")
    @GetMapping("/transcription")
    public String transcription(AudioTranscriptionRequest request) {
        log.info("调用 OpenAi 语音转文字 STT，参数：{}", request);

        String model = StrUtil.isBlank(request.getModel()) ? openAiDefaultTranscriptionModel : request.getModel();
        String language = StrUtil.isBlank(request.getLanguage()) ? openAiDefaultTranscriptionLanguage : request.getLanguage();
        // deepgram/nova-3 temperature 仅支持 0~1
        Float rawTemperature = request.getTemperature() == null ? openAiDefaultTranscriptionTemperature : request.getTemperature();
        Float temperature = Math.clamp(rawTemperature, 0f, 1f);
        // deepgram/nova-3 responseFormat 仅支持 verbose_json
        AudioTranscriptionOptions audioTranscriptionOptions = OpenAiAudioTranscriptionOptions.builder()
                .model(model)
                .language(language)
                .temperature(temperature)
                .responseFormat(AudioResponseFormat.VERBOSE_JSON)
                .build();

        org.springframework.core.io.Resource audioFile;
        if (StrUtil.isNotBlank(request.getAudioFilePath())) {
            // 获取本地语音文件
            audioFile = new FileSystemResource(request.getAudioFilePath());
        } else {
            log.info("本地语音文件地址为空，使用默认 ClasPath 路径下的语音文件");
            // 获取默认 ClasPath 路径下的语音文件
            audioFile = new ClassPathResource("/static/audios/tts-openai.mp3");
        }

        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioFile, audioTranscriptionOptions);
        AudioTranscriptionResponse response = openAiAudioTranscriptionModel.call(prompt);
        return response.getResult().getOutput();
    }

    /**
     * 构建 OpenAi Speech参数
     */
    private static TextToSpeechOptions buildSpeechOptions(AudioTTSRequest request, String model) {
        TextToSpeechOptions.Builder builder = TextToSpeechOptions.builder();

        if (StrUtil.isNotBlank(model)) {
            builder.model(model);
        }
        if (StrUtil.isNotBlank(request.getVoice())) {
            builder.voice(request.getVoice());
        }
        if (request.getSpeed() != null) {
            builder.speed(request.getSpeed());
        }
        if (StrUtil.isNotBlank(request.getResponseFormat())) {
            builder.format(request.getResponseFormat());
        }
        return builder.build();
    }
}
