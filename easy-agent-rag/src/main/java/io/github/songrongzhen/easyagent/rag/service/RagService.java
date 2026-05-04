package io.github.songrongzhen.easyagent.rag.service;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.loader.PdfDocumentLoader;
import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;
import io.github.songrongzhen.easyagent.rag.store.VectorStoreProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStoreProvider vectorStoreProvider;
    private final EasyAgentRagProperties properties;

    public RagService(VectorStoreProvider vectorStoreProvider, EasyAgentRagProperties properties) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.properties = properties;
    }

    public void indexPdfDocuments() {
        if (!properties.getPdf().isEnabled()) {
            log.info("PDF indexing is disabled");
            return;
        }

        PdfDocumentLoader loader = new PdfDocumentLoader(
                properties.getPdf().getResourcePath(),
                properties.getPdf().getChunkSize(),
                properties.getPdf().getChunkOverlap()
        );

        List<DocumentChunk> chunks = loader.load();
        if (chunks.isEmpty()) {
            log.info("No PDF chunks to index");
            return;
        }

        vectorStoreProvider.add(chunks);
        log.info("Indexed {} PDF chunks into vector store", chunks.size());
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

    public VectorStoreProvider getVectorStoreProvider() {
        return vectorStoreProvider;
    }
}
