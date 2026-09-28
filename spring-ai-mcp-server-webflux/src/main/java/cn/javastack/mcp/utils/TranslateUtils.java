package cn.javastack.mcp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 翻译工具类
 * 目前支持 中文 → 英文
 */
@Slf4j
public class TranslateUtils {

    private static final String TRANSLATE_URL = "https://api.mymemory.translated.net/get";
    private static final RestTemplate restTemplate = new RestTemplate();

    /**
     * text中包含中文时翻译为英文（去掉冠词提升检索相关性）
     * 失败或非中文则返回原文
     */
    public static String zhToEn(String text) {
        if (!containsCjk(text)) {
            return text;
        }

        try {
            String url = TRANSLATE_URL + "?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8) + "&langpair=" + URLEncoder.encode("zh-CN|en", StandardCharsets.UTF_8);
            Map<?, ?> body = restTemplate.getForObject(URI.create(url), Map.class);

            Object responseData = body != null ? body.get("responseData") : null;
            String translated = responseData instanceof Map<?, ?> rd ? String.valueOf(rd.get("translatedText")) : "";
            translated = translated.replaceFirst("^(?i)(the|a|an)\\s+", "").trim();
            if (StringUtils.hasText(translated) && !containsCjk(translated)) {
                log.info("中文关键词翻译：{} -> {}", text, translated);
                return translated;
            }
        } catch (RestClientException e) {
            log.warn("中文翻译失败，沿用原文：{}", text, e);
        }
        return text;
    }

    /**
     * 判断字符串是否包含中文字符（CJK统一汉字）
     */
    public static boolean containsCjk(String text) {
        return text != null && text.matches(".*[\\u4e00-\\u9fff].*");
    }
}
