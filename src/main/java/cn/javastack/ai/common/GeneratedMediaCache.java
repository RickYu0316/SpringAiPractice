package cn.javastack.ai.common;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生成的媒体文件临时缓存
 * 生成接口把图片/音频等媒体字节存入内存并返回下载地址，下载接口按 id 取出
 */
@Component
public class GeneratedMediaCache {

    /**
     * 媒体缓存过期时间（毫秒），默认 10 分钟
     */
    private static final long EXPIRE_MILLIS = 10 * 60 * 1000L;

    /**
     * 缓存项
     */
    public record CachedMedia(String id, byte[] data, String mediaType, long expireAt) {
    }

    private final Map<String, CachedMedia> cache = new ConcurrentHashMap<>();

    /**
     * 存入媒体数据，返回访问 id
     */
    public String store(byte[] data, String mediaType) {
        String id = UUID.randomUUID().toString().replace("-", "");
        cache.put(id, new CachedMedia(id, data, mediaType, System.currentTimeMillis() + EXPIRE_MILLIS));
        return id;
    }

    /**
     * 按 id 取媒体数据，不存在或已过期返回 null
     */
    public CachedMedia get(String id) {
        CachedMedia media = cache.get(id);
        if (media == null) {
            return null;
        }
        if (System.currentTimeMillis() > media.expireAt()) {
            cache.remove(id);
            return null;
        }
        return media;
    }
}
