package cn.javastack.ai.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 结构化输出 Controller
 */
@Tag(name = "结构化输出接口", description = "结构化输出接口")
@Slf4j
@RestController
@RequestMapping("/structured")
public class StructuredController {

    @Resource(name = "deepSeekChatClient")
    private ChatClient deepSeekChatClient;

    @Resource(name = "openAiChatClient")
    private ChatClient openAiChatClient;

    @Operation(summary = "JavaBean 类型1")
    @GetMapping("/chat1")
    public Object chat1(@Parameter(description = "歌手") @RequestParam(value = "singer") String singer) {
        return deepSeekChatClient.prompt()
                .user(u -> u.text("生成歌手 {singer} 的 5 首原创歌曲名称。")
                        .param("singer", singer))
                .call()
                .entity(SingerSongs.class);
    }

    @Operation(summary = "JavaBean 类型2")
    @GetMapping("/chat2")
    public Object chat2() {
        return deepSeekChatClient.prompt()
                .user(u -> u.text("生成 3 位中国流行歌手的各 5 首原创歌曲名称。"))
                .call()
                .entity(new ParameterizedTypeReference<List<SingerSongs>>() {});
    }

    @Operation(summary = "Map 类型1")
    @GetMapping("/chat3")
    public Map<String, String> chat3(@Parameter(description = "国家") @RequestParam(value = "country") String country) {
        return deepSeekChatClient.prompt()
                .user(u -> u.text("随机生成一个 {country} 地址，要有州（省份）、城市、具体街道、邮编等信息，不需要根节点，每个信息单独一个节点。")
                        .param("country", country))
                .call()
                .entity(new ParameterizedTypeReference<>() {
                });
    }

    @Operation(summary = "Map 类型2")
    @GetMapping("/chat4")
    public Map<String, Object> chat4(@Parameter(description = "国家") @RequestParam(value = "country") String country) {
        return deepSeekChatClient.prompt()
                .user(u -> u.text("随机生成一个 {country} 地址，要有州（省份）、城市、具体街道、邮编等信息，不需要根节点，每个信息单独一个节点。")
                        .param("country", country))
                .call()
                .entity(new MapOutputConverter());
    }

    @Operation(summary = "List 类型1")
    @GetMapping("/chat5")
    public List<String> chat5(@Parameter(description = "国家") @RequestParam(value = "country") String country) {
        return deepSeekChatClient.prompt()
                .user(u -> u.text("生成 {country} 的 10 个使用人数最多的 APP。")
                        .param("country", country))
                .call()
                .entity(new ParameterizedTypeReference<>() {
                });
    }

    @Operation(summary = "List 类型2")
    @GetMapping("/chat6")
    public List<String> chat6(@Parameter(description = "国家") @RequestParam(value = "country") String country) {
        return deepSeekChatClient.prompt()
                .user(u -> u.text("生成 {country} 的 10 个使用人数最多的 APP。")
                        .param("country", country))
                .call()
                .entity(new ListOutputConverter());
    }

    @Operation(summary = "JSON_OBJECT 格式")
    @GetMapping("/chat7")
    public String chat7(@Parameter(description = "输入问题，必须带有 JSON 字样") @RequestParam(value = "message") String message) {
        // 使用 json_object 原生结构化输出
        // DeepSeek ResponseFormat 没有 json_schema 原生结构化输出
        DeepSeekChatOptions chatOptions = DeepSeekChatOptions.builder().responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                .build();

        return deepSeekChatClient.prompt(new Prompt(message, chatOptions))
                .call()
                .content();
    }

    @Operation(summary = "JSON_SCHEMA 格式")
    @GetMapping("/chat8")
    public Object chat8(@Parameter(description = "国家") @RequestParam(value = "country") String country) {
        String jsonSchema = """
                {
                    "type": "object",
                    "properties": {
                        "country": { "type": "string" },
                        "address_list": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "state": { "type": "string" },
                                    "city": { "type": "string" },
                                    "address": { "type": "string" },
                                    "zipcode": { "type": "integer" }
                                },
                                "required": ["state", "city", "address", "zipcode"],
                                "additionalProperties": false
                            }
                        }
                    },
                    "required": ["country", "address_list"],
                    "additionalProperties": false
                }
                """;

        // 使用 OpenAi json_schema 原生结构化输出
        OpenAiChatModel.ResponseFormat responseFormat = OpenAiChatModel.ResponseFormat.builder()
                .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(jsonSchema)
                .build();

        return openAiChatClient.prompt()
                .user(u -> u.text("生成 3 个 {country} 地址。")
                        .param("country", country))
                .options(OpenAiChatOptions.builder().responseFormat(responseFormat))
                .call()
                .entity(CountryAddress.class);
    }

    /**
     * 歌手和歌曲
     */
    @JsonPropertyOrder({"singer", "songs"})
    record SingerSongs(String singer, List<String> songs) {
    }

    /**
     * 国家和地址列表
     */
    record CountryAddress(String country, @JsonProperty("address_list") List<Address> addressList) {
    }

    /**
     * 地址信息
     */
    record Address(String state, String city, String address, Integer zipcode) {
    }
}
