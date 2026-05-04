package io.github.songrongzhen.easyagent.rag.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryVectorStoreProvider implements VectorStoreProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStoreProvider.class);

    private final Map<String, DocumentChunk> store = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);

    @Override
    public void add(java.util.List<DocumentChunk> chunks) {
        for (DocumentChunk chunk : chunks) {
            String id = chunk.id() != null ? chunk.id() : "chunk-" + idCounter.incrementAndGet();
            DocumentChunk withId = new DocumentChunk(id, chunk.content(), chunk.source(), chunk.metadata(), chunk.embedding());
            store.put(id, withId);
        }
        log.info("Added {} chunks to in-memory store, total: {}", chunks.size(), store.size());
    }

    @Override
    public java.util.List<DocumentChunk> search(String query, int topK) {
        log.warn("In-memory vector store does not support semantic search, returning all documents");
        return store.values().stream()
                .limit(topK)
                .toList();
    }

    @Override
    public void delete(java.util.List<String> ids) {
        ids.forEach(store::remove);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getName() {
        return "in-memory";
    }
}
