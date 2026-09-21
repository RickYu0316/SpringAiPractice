package cn.javastack.ai.controller;

import cn.javastack.ai.dto.custom.MilvusSimilaritySearchRequest;
import cn.javastack.ai.service.VectorService;
import cn.javastack.ai.utils.VectorUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "RAG 检索增强生成接口", description = "RAG 检索增强生成接口")
@Slf4j
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    @Resource(name = "ragChatClient")
    private ChatClient ragChatClient;

    private final MilvusVectorStore milvusVectorStore;

    private final VectorService vectorService;

    /**
     * RAG问答 用户模板：{question_answer_context}为检索结果，{query}为用户问题
     */
    private static final String RAG_QA_TEMPLATE = """
            Context information is below, surrounded by backticks.
            Context:
            ```
            {question_answer_context}
            ```
            Answer the user's question using only the context above; if the answer is not in the context,
            tell the user the knowledge base does not contain the information and do not fabricate.
            User question: {query}
            """;

    @Operation(summary = "初始化 VectorStore")
    @GetMapping("/initVectorStore")
    public void initVectorStore() {
        vectorService.initVectorStore();
    }

    @Operation(summary = "检索相似文档")
    @GetMapping("/similaritySearch")
    public List<Document> similaritySearch(MilvusSimilaritySearchRequest request) {
        log.info("向量库检索相似文档，请求参数：{}", request.toString());
        return milvusVectorStore.doSimilaritySearch(VectorUtils.buildMilvusSearchRequest(request));
    }

    /**
     * RAG聊天：检索增强生成 + 多轮记忆
     */
    @Operation(summary = "RAG 检索增强聊天")
    @GetMapping("/ragChat")
    public String ragChat(MilvusSimilaritySearchRequest request) {
        log.info("RAG 检索增强聊天，请求参数：{}", request.toString());
        Assert.hasText(request.getMessage(), "message 不能为空");
        Assert.hasText(request.getConversationId(), "conversationId 不能为空");

        List<Document> documents = milvusVectorStore.doSimilaritySearch(VectorUtils.buildMilvusSearchRequest(request));
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        String userMessage = new PromptTemplate(RAG_QA_TEMPLATE)
                .render(Map.of("question_answer_context", context, "query", request.getMessage()));

        return ragChatClient.prompt(userMessage)
                .advisors(ap -> {
                    ap.param(ChatMemory.CONVERSATION_ID, request.getConversationId());
                })
                .call()
                .content();
    }
}
