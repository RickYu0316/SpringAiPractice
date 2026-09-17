package cn.javastack.mcp.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TranslateUtils 功能测试（依赖外网翻译接口）
 */
class TranslateUtilsTest {

    /** 中文应翻译为英文，结果不含中文字符 */
    @Test
    void testTranslateChineseToEnglish() {
        String result = TranslateUtils.zhToEn("小狗");
        assertThat(result).isNotBlank();
        assertThat(result).isNotEqualTo("小狗");
        assertThat(result).doesNotMatch(".*[\\u4e00-\\u9fff].*");
    }

    /** 英文不翻译，直接返回原文 */
    @Test
    void testEnglishTextUnchanged() {
        assertThat(TranslateUtils.zhToEn("dog")).isEqualTo("dog");
    }

    /** 空字符串直接返回 */
    @Test
    void testEmptyStringUnchanged() {
        assertThat(TranslateUtils.zhToEn("")).isEmpty();
    }

    /** null输入应安全返回null，不抛异常 */
    @Test
    void testNullSafely() {
        assertThat(TranslateUtils.zhToEn(null)).isNull();
    }
}
