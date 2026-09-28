package cn.javastack.ai.utils;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 文档内容解析工具
 */
@Slf4j
public final class DocumentParseUtils {

    private DocumentParseUtils() {
    }

    /**
     * 支持解析的文件扩展名
     */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "docm", "dotx",
            "txt", "md", "rtf", "odt");

    /**
     * 提取文本的最大字符数
     */
    private static final int MAX_TEXT_LENGTH = 5_000_000;

    /**
     * 线程安全: Tika实例内部持有 AutoDetectParser 等解析器
     */
    private static final Tika TIKA = new Tika();

    /**
     * 提取文档为纯文本内容
     */
    public static String extractContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("解析文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (StrUtil.isBlank(filename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        log.info("文档内容解析工具提取文档内容，文件名：{}", filename);

        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型 ." + extension + "，仅支持：" + SUPPORTED_EXTENSIONS);
        }

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        try (InputStream inputStream = file.getInputStream()) {
            String content = TIKA.parseToString(inputStream, metadata, MAX_TEXT_LENGTH).trim();
            if (content.isEmpty()) {
                throw new IllegalArgumentException("无法从文档中提取文本内容");
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("读取文档内容失败：" + filename, e);
        } catch (TikaException e) {
            throw new IllegalStateException("解析文档内容失败：" + filename, e);
        }
    }
}
