package cn.javastack.ai.controller;

import cn.hutool.core.util.StrUtil;
import cn.javastack.ai.utils.VectorUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Embedding 嵌入向量模型接口")
@Slf4j
@RestController
@RequestMapping("/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    /**
     * 嵌入模型，实际使用 OpenRouter voyage-4
     */
    private final OpenAiEmbeddingModel embeddingModel;

    @Value("${spring.ai.openai.embedding.model}")
    private String embeddingModelName;

    /**
     * 系统已有 FAQ
     */
    private static final String[] FAQS = new String[]{
            "能不能优惠一点",
            "有优惠吗",
            "现在是什么价",
            "课程双十一有活动吗",
            "如何修改密码",
            "能用微信支付吗",
            "如何获取源码",
            "有效期多长",
            "登录账号密码怎么重置",
            "如何联系客服"
    };

    /**
     * FAQ 嵌入向量缓存
     */
    private volatile List<float[]> faqVectors;

    /**
     * 获取 FAQ 嵌入向量（懒加载）
     */
    private List<float[]> getFaqVectors() {
        List<float[]> cached = faqVectors;
        if (cached == null) {
            synchronized (this) {
                cached = faqVectors;
                if (cached == null) {
                    cached = embeddingModel.embed(Arrays.asList(FAQS));
                    faqVectors = cached;
                }
            }
        }
        return cached;
    }

    @Operation(summary = "文本转嵌入向量")
    @GetMapping("/textEmbed")
    public Map<String, Object> textEmbed(@Parameter(description = "待向量化的文本") @RequestParam("text") String text) {
        log.info("文本转嵌入向量，文本：{}", text);
        // 转成 float[] 向量
        float[] vector = embeddingModel.embed(text);
        // 生成完整响应
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));

        Map<String, Object> result = new HashMap<>();
        result.put("model", embeddingModelName);
        result.put("dimensions", vector.length);
        result.put("vector", response.getResults().getFirst().getOutput());
        return result;
    }

    @Operation(summary = "比较嵌入向量")
    @GetMapping("/compareDistance")
    public String compareDistance(@Parameter(description = "待比较的问题") @RequestParam(value = "question") String question) {
        if (StrUtil.hasBlank(question)) {
            return "question 不能为空";
        }
        log.info("比较嵌入向量，问题：{}", question);

        float[] queryVector = embeddingModel.embed(question);
        List<float[]> faqVectors = getFaqVectors();

        StringBuilder resultBuilder = new StringBuilder();
        // 计算欧氏距离（数值越小，语义越接近）
        resultBuilder.append("=== 欧氏距离 === \n");
        double selfEuclideanDistance = VectorUtils.euclideanDistance(queryVector, queryVector);
        resultBuilder.append("%s -> %s = %s%n".formatted(question, question, selfEuclideanDistance));
        for (int i = 0; i < faqVectors.size(); i++) {
            double distance = VectorUtils.euclideanDistance(queryVector, faqVectors.get(i));
            resultBuilder.append("%s -> %s = %s%n".formatted(question, FAQS[i], distance));
        }

        // 计算余弦相似度（数值越大，语义越接近）
        resultBuilder.append("=== 余弦相似度 === \n");
        double selfCosineDistance = VectorUtils.cosineSimilarity(queryVector, queryVector);
        resultBuilder.append("%s -> %s = %s%n".formatted(question, question, selfCosineDistance));
        for (int i = 0; i < faqVectors.size(); i++) {
            double distance = VectorUtils.cosineSimilarity(queryVector, faqVectors.get(i));
            resultBuilder.append("%s -> %s = %s%n".formatted(question, FAQS[i], distance));
        }
        return resultBuilder.toString();
    }
}
