package cn.javastack.ai.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 生成的媒体文件临时缓存
 * 生成接口把图片/音频/视频等媒体字节存入内存并返回下载地址，下载接口按 id 取出
 * 基于Caffeine实现，自动过期清理 + 容量上限，避免缓存无限增长
 */
@Component
public class GeneratedMediaCache {

    /**
     * 媒体缓存过期时间（毫秒），默认 10 分钟
     */
    private static final long EXPIRE_MILLIS = 10 * 60 * 1000L;

    /**
     * 缓存总容量上限，默认 512MB
     * 按实际数据字节数计重，超出后按访问频率/时间淘汰最不常用条目，防止内存被大对象撑爆
     */
    private static final long MAX_WEIGHT_BYTES = 512L * 1024 * 1024;

    /**
     * 缓存项
     */
    public record CachedMedia(String id, byte[] data, String mediaType) {
    }

    private final Cache<String, CachedMedia> cache;

    public GeneratedMediaCache() {
        this.cache = Caffeine.newBuilder()
                // 同步执行缓存维护（过期/超容淘汰），避免后台线程异步导致的时延
                .executor(Runnable::run)
                .expireAfterWrite(EXPIRE_MILLIS, TimeUnit.MILLISECONDS)
                .maximumWeight(MAX_WEIGHT_BYTES)
                .weigher((String id, CachedMedia media) -> media.data() == null ? 0 : media.data().length)
                .build();
    }

    /**
     * 存入媒体数据，返回访问 id
     */
    public String store(byte[] data, String mediaType) {
        String id = UUID.randomUUID().toString().replace("-", "");
        cache.put(id, new CachedMedia(id, data, mediaType));
        return id;
    }

    /**
     * 按 id 取媒体数据，不存在或已过期返回 null
     * Caffeine的过期条目不命中时即视为缺失（惰性清除），并会在后台维护周期内真正移除
     */
    public CachedMedia get(String id) {
        return cache.getIfPresent(id);
    }
}
