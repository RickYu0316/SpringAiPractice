package cn.javastack.ai.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenRouterResponseUtils 单元测试
 * 覆盖：正常内容提取、错误信息提取、空响应健壮性
 */
class OpenRouterResponseUtilsTest {

    @Test
    void extractContentReturnsText() {
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", "hello")
                )));
        assertThat(OpenRouterResponseUtils.extractContent(response)).isEqualTo("hello");
    }

    @Test
    void extractContentReturnsNullWhenNoChoices() {
        assertThat(OpenRouterResponseUtils.extractContent(Map.<String, Object>of("error", "x"))).isNull();
        assertThat(OpenRouterResponseUtils.extractContent((Map<String, Object>) null)).isNull();
    }

    @Test
    void extractErrorReturnsMessage() {
        Map<String, Object> response = Map.of(
                "error", Map.of("message", "视频下载失败", "code", 400));
        assertThat(OpenRouterResponseUtils.extractError(response)).isEqualTo("视频下载失败");
    }

    @Test
    void extractErrorReturnsNullWhenNoError() {
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "ok"))));
        assertThat(OpenRouterResponseUtils.extractError(response)).isNull();
        assertThat(OpenRouterResponseUtils.extractError((Map<String, Object>) null)).isNull();
    }
}
