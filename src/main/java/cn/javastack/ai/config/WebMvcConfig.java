package cn.javastack.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 异步支持
 * 为流式接口（Flux/SSE等异步请求处理）提供可复用的线程池 + 默认超时
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Spring MVC 异步请求处理线程池
     */
    @Bean(name = "webMvcAsyncTaskExecutor")
    public ThreadPoolTaskExecutor webMvcAsyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mvc-async-");
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(webMvcAsyncTaskExecutor());
        configurer.setDefaultTimeout(60_000L);
    }
}
