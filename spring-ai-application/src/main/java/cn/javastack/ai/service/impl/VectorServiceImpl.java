package cn.javastack.ai.service.impl;

import cn.javastack.ai.dto.custom.CustomJsonMetadataGenerator;
import cn.javastack.ai.service.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量数据库 ServiceImpl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorServiceImpl implements VectorService {

    private final MilvusVectorStore milvusVectorStore;

    /**
     * JSON元数据生成器
     */
    private static final CustomJsonMetadataGenerator JSON_METADATA_GENERATOR = new CustomJsonMetadataGenerator("products", "products.json");

    /**
     * 初始化 VectorStore
     */
    @Override
    public void initVectorStore() {
        String jsonFilePath = "/static/knowledgeBase/products.json";
        List<Document> jsonDocuments = getJsonDocuments(jsonFilePath);

        String pdfFilePath1 = "/static/knowledgeBase/Java开发手册.pdf";
        String pdfFilePath2 = "/static/knowledgeBase/星轨咖啡机手册.pdf";
        List<Document> pdfDocuments1 = getPDFDocuments(pdfFilePath1);
        List<Document> pdfDocuments2 = getPDFDocuments(pdfFilePath2);

        milvusVectorStore.doAdd(jsonDocuments);
        milvusVectorStore.doAdd(pdfDocuments1);
        milvusVectorStore.doAdd(pdfDocuments2);
        log.info("初始化 VectorStore 完成！");
    }

    /**
     * 获取 Json Documents
     */
    private List<Document> getJsonDocuments(String jsonFilePath) {
        ClassPathResource jsonResource = new ClassPathResource(jsonFilePath);

        JsonReader jsonReader = new JsonReader(jsonResource, JSON_METADATA_GENERATOR,
                "name", "price", "stock", "description", "tags");
        return jsonReader.get();
    }

    /**
     * 获取 PDF Documents
     */
    private List<Document> getPDFDocuments(String pdfFilePath) {
        ClassPathResource pdfResource = new ClassPathResource(pdfFilePath);
        // 每一页作为一个 Document
        PdfDocumentReaderConfig pdfDocumentReaderConfig = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                .withPagesPerDocument(1)
                .build();

        PagePdfDocumentReader pagePdfDocumentReader = new PagePdfDocumentReader(pdfResource, pdfDocumentReaderConfig);
        return pagePdfDocumentReader.read();
    }
}
