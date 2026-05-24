package io.github.songrongzhen.easyagent.rag.service;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.loader.ExcelDocumentLoader;
import io.github.songrongzhen.easyagent.rag.loader.PdfDocumentLoader;
import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;
import io.github.songrongzhen.easyagent.rag.store.VectorStoreProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.util.List;

public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStoreProvider vectorStoreProvider;
    private final EasyAgentRagProperties properties;

    public RagService(VectorStoreProvider vectorStoreProvider, EasyAgentRagProperties properties) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.properties = properties;
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
}
