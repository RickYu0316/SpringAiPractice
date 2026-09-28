package cn.javastack.ai.dto.custom;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Milvus 相似度搜索请求对象
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MilvusSimilaritySearchRequest extends SimilaritySearchRequest {

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * Milvus原生过滤表达式，不为空会覆盖 FilterExpression
     * 示例：metadata[\"stock\"] >= 30
     */
    private String nativeExpression;

    /**
     * Milvus搜索调优参数
     * nprobe 设置分区数量，提高召回率
     */
    private String searchParamsJson = "{\"nprobe\":32}";
}
