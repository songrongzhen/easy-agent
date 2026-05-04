package io.github.songrongzhen.easyagent.rag.store;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PgVectorStoreProvider implements VectorStoreProvider {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreProvider.class);

    private final VectorStore vectorStore;

    public PgVectorStoreProvider(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void add(List<DocumentChunk> chunks) {
        List<Document> documents = chunks.stream()
                .map(this::toSpringAiDocument)
                .toList();
        vectorStore.add(documents);
        log.info("Added {} documents to PGVector store", documents.size());
    }

    @Override
    public List<DocumentChunk> search(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results.stream()
                .map(this::toDocumentChunk)
                .toList();
    }

    @Override
    public void delete(List<String> ids) {
        vectorStore.delete(ids);
    }

    @Override
    public void deleteAll() {
        log.warn("PGVector store does not support deleteAll directly, please truncate the table manually");
    }

    @Override
    public boolean isAvailable() {
        try {
            vectorStore.similaritySearch(SearchRequest.builder().query("test").topK(1).build());
            return true;
        } catch (Exception e) {
            log.warn("PGVector store is not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getName() {
        return "pgvector";
    }

    private Document toSpringAiDocument(DocumentChunk chunk) {
        Map<String, Object> metadata = new HashMap<>(chunk.metadata() != null ? chunk.metadata() : Map.of());
        metadata.put("source", chunk.source());
        return new Document(chunk.id(), chunk.content(), metadata);
    }

    private DocumentChunk toDocumentChunk(Document doc) {
        Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
        String source = (String) metadata.remove("source");
        return new DocumentChunk(doc.getId(), doc.getText(), source, metadata, null);
    }
}
