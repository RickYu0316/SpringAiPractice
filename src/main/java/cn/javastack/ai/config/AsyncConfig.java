package cn.javastack.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务线程池配置
 */
@Configuration
public class AsyncConfig {

    /**
     * 视频生成任务执行线程池
     */
    @Bean(name = "videoTaskExecutor")
    public ThreadPoolTaskExecutor videoTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("video-task-");
        executor.initialize();
        return executor;
    }
}
