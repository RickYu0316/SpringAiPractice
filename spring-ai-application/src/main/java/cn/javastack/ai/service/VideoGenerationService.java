package cn.javastack.ai.service;

import java.util.Map;

/**
 * 视频生成 Service
 */
public interface VideoGenerationService {

    /**
     * 提交视频生成任务，立即返回本地 taskId，后台线程异步生成
     */
    Map<String, Object> generate(String prompt, String model, Integer duration, String resolution, String aspectRatio);

    /**
     * 查询视频生成任务状态
     */
    Map<String, Object> getStatus(String taskId);

    /**
     * 视频理解（多模态）
     */
    String understandVideo(String message, String videoUrl, String model);
}
