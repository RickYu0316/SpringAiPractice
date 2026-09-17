package cn.javastack.ai.config;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/*
* 加载本地的 .env 文件（不提交git），防止API-KEY泄漏
* 生产Docker镜像中不能有 .env 同名文件，要使用命令行参数或者系统环境变量（SpringBoot原生支持），或者使用 prod.env 文件加载系统环境变量
* 生产Docker镜像中如果有 .env 同名文件，.env 中的值会覆盖Docker系统环境变量（addFirst优先级更高）
* */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(@NonNull ConfigurableEnvironment environment, @NonNull SpringApplication application) {
        // 多模块工程：优先读取当前工作目录下的 .env，找不到时回退到父目录（仓库根目录）查找
        Path dotenvPath = Path.of(".env");
        if (!Files.exists(dotenvPath)) {
            dotenvPath = Path.of("..", ".env");
        }
        if (!Files.exists(dotenvPath)) {
            log.debug(".env file not found, skipping");
            return;
        }

        try {
            Map<String, Object> properties = new HashMap<>();
            Files.readAllLines(dotenvPath).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> {
                        int idx = line.indexOf('=');
                        if (idx > 0) {
                            String key = line.substring(0, idx).trim();
                            String value = line.substring(idx + 1).trim();
                            if (value.length() >= 2
                                    && ((value.startsWith("\"") && value.endsWith("\""))
                                    || (value.startsWith("'") && value.endsWith("'")))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            properties.put(key, value);
                        }
                    });

            if (!properties.isEmpty()) {
                environment.getPropertySources().addFirst(new MapPropertySource("dotenv", properties));
                log.info("Loaded {} properties from .env file", properties.size());
            }
        } catch (IOException e) {
            log.warn("Failed to read .env file: {}", e.getMessage());
        }
    }
}
