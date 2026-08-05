package cn.javastack.ai.utils;

/**
 * 媒体类型工具
 */
public final class MediaTypeUtils {

    private MediaTypeUtils() {
    }

    /**
     * 根据媒体类型获取文件后缀
     */
    public static String fileExtension(String mediaType) {
        return switch (mediaType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/wav" -> "wav";
            case "audio/flac" -> "flac";
            case "video/mp4" -> "mp4";
            default -> "bin";
        };
    }
}
