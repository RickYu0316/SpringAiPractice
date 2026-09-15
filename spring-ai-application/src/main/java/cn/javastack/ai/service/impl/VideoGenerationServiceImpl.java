package cn.javastack.ai.service.impl;

import cn.javastack.ai.client.OpenRouterClient;
import cn.javastack.ai.common.GeneratedMediaCache;
import cn.javastack.ai.common.VideoTaskCache;
import cn.javastack.ai.service.VideoGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoGenerationServiceImpl implements VideoGenerationService {

    /**
     * 视频媒体类型
     */
    private static final String VIDEO_MEDIA_TYPE = "video/mp4";

    /**
     * 视频任务状态轮询间隔（毫秒）
     */
    private static final long POLL_INTERVAL_MILLIS = 5_000L;

    /**
     * 视频任务最大等待时间 10分钟
     */
    private static final long MAX_WAIT_MILLIS = 10 * 60_000L;

    private final OpenRouterClient openRouterClient;

    private final GeneratedMediaCache generatedMediaCache;

    private final VideoTaskCache videoTaskCache;

    private final ThreadPoolTaskExecutor videoTaskExecutor;

    /**
    * 提交视频生成任务，立即返回本地 taskId，后台线程异步生成，不阻塞线程
    */
    @Override
    public Map<String, Object> generate(String prompt, String model, Integer duration, String resolution, String aspectRatio) {
        String jobId = openRouterClient.submitVideo(prompt, model, duration, resolution, aspectRatio);
        VideoTaskCache.VideoTask task = new VideoTaskCache.VideoTask(jobId);
        String taskId = videoTaskCache.create(task);
        try {
            videoTaskExecutor.execute(() -> pollAndDownload(taskId, task, jobId));
        } catch (RuntimeException e) {
            task.setError("任务提交到执行队列失败：" + e.getMessage());
            task.setStatus(VideoTaskCache.STATUS_FAILED);
            log.error("视频生成任务入队失败，taskId：{}，jobId：{}", taskId, jobId, e);
        }
        log.info("视频生成任务已受理，taskId：{}，jobId：{}", taskId, jobId);
        return Map.of("taskId", taskId, "status", task.getStatus());
    }

    /**
    * 查询视频生成状态，已完成则返回下载地址
    */
    @Override
    public Map<String, Object> getStatus(String taskId) {
        VideoTaskCache.VideoTask task = videoTaskCache.get(taskId);
        if (task == null) {
            return Map.of("taskId", taskId, "status", "not_found");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("status", task.getStatus());
        if (VideoTaskCache.STATUS_COMPLETED.equals(task.getStatus())) {
            String base = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
            result.put("mediaType", VIDEO_MEDIA_TYPE);
            result.put("urls", List.of(base + "/video/download/" + task.getMediaId()));
        }
        if (task.getError() != null) {
            result.put("error", task.getError());
        }
        return result;
    }

    /**
     * 后台轮询视频任务状态，完成后下载远程视频并缓存，失败则记录错误
     */
    private void pollAndDownload(String taskId, VideoTaskCache.VideoTask task, String jobId) {
        try {
            task.setStatus(VideoTaskCache.STATUS_RUNNING);
            waitForCompleted(jobId);
            byte[] data = openRouterClient.downloadVideo(jobId);
            String mediaId = generatedMediaCache.store(data, VIDEO_MEDIA_TYPE);
            task.setMediaId(mediaId);
            task.setStatus(VideoTaskCache.STATUS_COMPLETED);
            log.info("视频生成完成，taskId：{}，jobId：{}，mediaId：{}", taskId, jobId, mediaId);
        } catch (Exception e) {
            log.error("视频生成失败，taskId：{}，jobId：{}：{}", taskId, jobId, e.getMessage(), e);
            task.setError(e.getMessage());
            task.setStatus(VideoTaskCache.STATUS_FAILED);
        }
    }

    /**
     * 轮询视频任务直至完成，失败/超时则抛出异常
     */
    @SuppressWarnings("BusyWait")
    private void waitForCompleted(String jobId) {
        long deadline = System.currentTimeMillis() + MAX_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> status = openRouterClient.getVideoStatus(jobId);
            String state = status != null && status.get("status") != null
                    ? status.get("status").toString()
                    : "";
            switch (state) {
                case "completed" -> {
                    return;
                }
                case "failed", "cancelled", "expired" ->
                        throw new RuntimeException("视频生成失败：" + status.get("error"));
                default -> {
                    try {
                        Thread.sleep(POLL_INTERVAL_MILLIS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("视频生成任务被中断，jobId：" + jobId, e);
                    }
                }
            }
        }
        throw new RuntimeException("视频生成超时，jobId：" + jobId);
    }

    @Override
    public String understandVideo(String message, String videoUrl, String model) {
        return openRouterClient.understandVideo(message, videoUrl, model);
    }
}
