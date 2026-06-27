package io.github.songrongzhen.easyagent.rag.service;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.loader.DocumentLoader;
import io.github.songrongzhen.easyagent.rag.loader.ExcelDocumentLoader;
import io.github.songrongzhen.easyagent.rag.loader.PdfDocumentLoader;
import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;
import io.github.songrongzhen.easyagent.rag.store.VectorStoreProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStoreProvider vectorStoreProvider;
    private final EasyAgentRagProperties properties;
    private final List<DocumentLoader> documentLoaders;

    public RagService(VectorStoreProvider vectorStoreProvider, EasyAgentRagProperties properties) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.properties = properties;
        this.documentLoaders = createDocumentLoaders(properties);
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        
        try {
            indexAllDocuments();
            int count = vectorStoreProvider.count();
            if (count > 0) {
                log.info("RAG initialized with {} document chunks", count);
            }
        } catch (Exception e) {
            log.error("Failed to initialize RAG documents: {}", e.getMessage(), e);
        }
    }

    public void indexPdfDocuments() {
        if (!properties.getPdf().isEnabled()) {
            return;
        }

        PdfDocumentLoader loader = new PdfDocumentLoader(
                properties.getPdf().getResourcePath(),
                properties.getPdf().getChunkSize(),
                properties.getPdf().getChunkOverlap()
        );

        List<DocumentChunk> chunks = loader.load();
        if (!chunks.isEmpty()) {
            vectorStoreProvider.add(chunks);
            log.info("Loaded {} PDF chunks from {}", chunks.size(), properties.getPdf().getResourcePath());
        }
    }

    public void indexExcelDocuments() {
        if (!properties.getExcel().isEnabled()) {
            return;
        }

        ExcelDocumentLoader loader = new ExcelDocumentLoader(
                properties.getExcel().getResourcePath()
        );

        List<DocumentChunk> chunks = loader.load();
        if (!chunks.isEmpty()) {
            vectorStoreProvider.add(chunks);
            log.info("Loaded {} Excel chunks from {}", chunks.size(), properties.getExcel().getResourcePath());
        }
    }

    public void indexAllDocuments() {
        indexPdfDocuments();
        indexExcelDocuments();
    }

    /**
     * 添加运行时文档到知识库。
     */
    public synchronized List<DocumentChunk> addDocument(String filename, InputStream inputStream) throws IOException {
        return addDocument(generateDocumentId(filename), filename, inputStream);
    }

    /**
     * 添加指定文档ID的运行时文档到知识库。
     */
    public synchronized List<DocumentChunk> addDocument(String documentId, String filename, InputStream inputStream) throws IOException {
        validateDocumentInput(documentId, filename, inputStream);
        DocumentLoader loader = resolveLoader(filename);
        List<DocumentChunk> chunks = loader.load(documentId, filename, inputStream);
        if (!chunks.isEmpty()) {
            vectorStoreProvider.deleteByDocumentId(documentId);
            vectorStoreProvider.add(chunks);
            log.info("Added runtime document: documentId={}, filename={}, chunks={}", documentId, filename, chunks.size());
        }
        return chunks;
    }

    /**
     * 删除指定来源的文档块。
     */
    public synchronized void deleteBySource(String source) {
        vectorStoreProvider.deleteBySource(source);
    }

    /**
     * 删除指定文档ID的文档块。
     */
    public synchronized void deleteByDocumentId(String documentId) {
        vectorStoreProvider.deleteByDocumentId(documentId);
    }

    /**
     * 清空知识库索引。
     */
    public synchronized void clearIndex() {
        vectorStoreProvider.deleteAll();
    }

    /**
     * 清空并重建默认知识库索引。
     */
    public synchronized void rebuildIndex() {
        vectorStoreProvider.deleteAll();
        indexAllDocuments();
        log.info("RAG index rebuilt with {} document chunks", vectorStoreProvider.count());
    }

    public List<DocumentChunk> search(String query, int topK) {
        return vectorStoreProvider.search(query, topK);
    }

    public List<DocumentChunk> search(String query) {
        return search(query, 5);
    }

    public String searchAndConcat(String query, int topK) {
        List<DocumentChunk> results = search(query, topK);
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (DocumentChunk chunk : results) {
            sb.append("[Source: ").append(chunk.source()).append("]\n");
            sb.append(chunk.content());
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    public String searchAndConcat(String query) {
        return searchAndConcat(query, 5);
    }

    public boolean hasKnowledgeBase() {
        try {
            return vectorStoreProvider.count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public VectorStoreProvider getVectorStoreProvider() {
        return vectorStoreProvider;
    }

    public List<DocumentChunk> getAllDocuments() {
        try {
            return vectorStoreProvider.getAll();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<DocumentLoader> createDocumentLoaders(EasyAgentRagProperties properties) {
        List<DocumentLoader> loaders = new ArrayList<>();
        if (properties.getPdf().isEnabled()) {
            loaders.add(new PdfDocumentLoader(
                    properties.getPdf().getResourcePath(),
                    properties.getPdf().getChunkSize(),
                    properties.getPdf().getChunkOverlap()
            ));
        }
        if (properties.getExcel().isEnabled()) {
            loaders.add(new ExcelDocumentLoader(properties.getExcel().getResourcePath()));
        }
        return loaders;
    }

    private DocumentLoader resolveLoader(String filename) {
        return documentLoaders.stream()
                .filter(loader -> loader.supports(filename))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported document type: " + filename));
    }

    private void validateDocumentInput(String documentId, String filename, InputStream inputStream) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream must not be null");
        }
    }

    private String generateDocumentId(String filename) {
        String source = filename != null && !filename.isBlank() ? filename : "document";
        return source + "-" + UUID.randomUUID();
    }
}
