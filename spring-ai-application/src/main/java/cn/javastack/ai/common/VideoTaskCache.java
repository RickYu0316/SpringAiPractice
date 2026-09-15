package cn.javastack.ai.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 视频生成任务临时缓存
 */
@Component
public class VideoTaskCache {

    public static final String STATUS_PENDING = "pending";

    public static final String STATUS_RUNNING = "running";

    public static final String STATUS_COMPLETED = "completed";

    public static final String STATUS_FAILED = "failed";

    /**
     * 任务记录过期时间 30分钟，任务完成后取走结果，记录自动清理
     */
    private static final long TASK_EXPIRE_MILLIS = 30 * 60 * 1000L;

    /**
     * 视频生成任务
     */
    @Getter
    public static class VideoTask {

        private final String jobId;

        @Setter
        private volatile String status = STATUS_PENDING;

        @Setter
        private volatile String mediaId;

        @Setter
        private volatile String error;

        public VideoTask(String jobId) {
            this.jobId = jobId;
        }

    }

    private final Cache<String, VideoTask> tasks = Caffeine.newBuilder()
            .expireAfterWrite(TASK_EXPIRE_MILLIS, TimeUnit.MILLISECONDS)
            .build();

    /**
     * 创建任务并返回本地 taskId
     */
    public String create(VideoTask task) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        tasks.put(taskId, task);
        return taskId;
    }

    /**
     * 按 taskId 获取任务，不存在或已过期返回 null
     */
    public VideoTask get(String taskId) {
        return tasks.getIfPresent(taskId);
    }
}
