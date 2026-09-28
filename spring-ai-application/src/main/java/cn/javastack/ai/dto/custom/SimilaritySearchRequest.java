package cn.javastack.ai.dto.custom;

import lombok.Data;

/**
 * 相似度搜索请求对象
 */
@Data
public class SimilaritySearchRequest {

    private String message;

    /**
     * 返回相似度最高的 K 条记录
     */
    private int topK = 5;

    /**
     * 相似度阈值，只有相似度高于该值的记录会被返回
     */
    private double similarityThreshold = 0.3d;

    /**
     * 过滤表达式，在搜索结果中过滤记录
     * 示例：price >= 1000 && price <= 3000 && stock > 50
     *      tags in ['安全', '家居']
     */
    private String filterExpression;
}
