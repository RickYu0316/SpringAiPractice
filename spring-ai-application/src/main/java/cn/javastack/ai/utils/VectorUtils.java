package cn.javastack.ai.utils;

import cn.hutool.core.util.StrUtil;
import cn.javastack.ai.dto.custom.MilvusSimilaritySearchRequest;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.milvus.MilvusSearchRequest;

import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 向量工具类
 * 用于 Embedding、语义检索、相似度计算等
 */
public class VectorUtils {

    /**
     * 用于判断零向量的阈值
     */
    private static final double ZERO_THRESHOLD = 1e-12;

    /**
     * 计算欧氏距离
     * 数值越小，表示两个向量在空间中越接近
     */
    public static double euclideanDistance(float[] a, float[] b) {
        validateVectors(a, b);
        return Math.sqrt(IntStream.range(0, a.length).mapToDouble(i -> {
            double diff = a[i] - b[i];
            return diff * diff;
        }).sum());
    }

    /**
     * 计算余弦相似度
     * 取值范围 [-1, 1]，数值越大表示语义越相似
     * 在向量检索和 RAG 场景中是最常用指标
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        validateVectors(a, b);

        double dotProduct = dot(a, b);
        double normA = norm(a);
        double normB = norm(b);

        if (normA < ZERO_THRESHOLD || normB < ZERO_THRESHOLD) {
            throw new IllegalArgumentException("向量值不能为 0");
        }

        // 计算余弦相似度
        double similarity = dotProduct / (normA * normB);
        // 修正浮点误差
        return Math.clamp(similarity, -1.0, 1.0);
    }

    /**
     * 计算两个向量的点积
     */
    private static double dot(float[] a, float[] b) {
        return IntStream.range(0, a.length).mapToDouble(i -> a[i] * b[i]).sum();
    }

    /**
     * 计算向量的模长
     */
    private static double norm(float[] v) {
        return Math.sqrt(IntStream.range(0, v.length).mapToDouble(i -> v[i] * v[i]).sum());
    }

    /**
     * 校验向量参数合法性
     */
    private static void validateVectors(float[] a, float[] b) {
        Objects.requireNonNull(a, "向量 A 不能为空");
        Objects.requireNonNull(b, "向量 B 不能为空");

        if (a.length == 0 || b.length == 0) {
            throw new IllegalArgumentException("向量不能为空");
        }

        if (a.length != b.length) {
            throw new IllegalArgumentException("两个向量的维度必须一致");
        }
    }

    /**
     * 构建 Milvus检索请求
     */
    public static SearchRequest buildMilvusSearchRequest(MilvusSimilaritySearchRequest request) {
        MilvusSearchRequest.MilvusBuilder milvusBuilder = MilvusSearchRequest.milvusBuilder();
        milvusBuilder.query(request.getMessage());
        milvusBuilder.topK(request.getTopK());
        milvusBuilder.similarityThreshold(request.getSimilarityThreshold());

        // FilterExpression
        if (StrUtil.isNotBlank(request.getFilterExpression())) {
            milvusBuilder.filterExpression(request.getFilterExpression());
        }
        // Milvus NativeExpression
        if (StrUtil.isNotBlank(request.getNativeExpression())) {
            milvusBuilder.nativeExpression(request.getNativeExpression());
        }
        // Milvus SearchParamsJson
        if (StrUtil.isNotBlank(request.getSearchParamsJson())) {
            milvusBuilder.searchParamsJson(request.getSearchParamsJson());
        }
        return milvusBuilder.build();
    }
}
