package cn.javastack.ai.dto.custom;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.reader.JsonMetadataGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义元数据生成器
 */
public record CustomJsonMetadataGenerator(String dataset, String source) implements JsonMetadataGenerator {

    @Override
    public @NonNull Map<String, Object> generate(@NonNull Map<String, Object> jsonMap) {
        Map<String, Object> metadata = new HashMap<>(jsonMap);

        // 设置数据集、数据来源
        metadata.put("dataset", this.dataset());
        metadata.put("source", this.source());
        return metadata;
    }
}
