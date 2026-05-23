package io.github.songrongzhen.easyagent.rag.store;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.search.SearchStrategy;
import io.github.songrongzhen.easyagent.rag.search.SearchStrategyFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryVectorStoreProvider implements VectorStoreProvider {

    private final Map<String, DocumentChunk> store = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);
    private final SearchStrategy searchStrategy;

    public InMemoryVectorStoreProvider(SearchStrategy searchStrategy) {
        this.searchStrategy = searchStrategy;
    }

    public InMemoryVectorStoreProvider(EasyAgentRagProperties properties) {
        this.searchStrategy = SearchStrategyFactory.create(properties, null);
    }

    @Override
    public void add(List<DocumentChunk> chunks) {
        for (DocumentChunk chunk : chunks) {
            String id = chunk.id() != null ? chunk.id() : "chunk-" + idCounter.incrementAndGet();
            DocumentChunk withId = new DocumentChunk(id, chunk.content(), chunk.source(), chunk.metadata(), chunk.embedding());
            store.put(id, withId);
        }
    }

    @Override
    public List<DocumentChunk> search(String query, int topK) {
        List<DocumentChunk> allDocs = new ArrayList<>(store.values());
        if (allDocs.isEmpty()) {
            return List.of();
        }
        
        return searchStrategy.search(query, allDocs, topK);
    }

    @Override
    public void delete(List<String> ids) {
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
        return "in-memory-" + searchStrategy.getName();
    }

    public SearchStrategy getSearchStrategy() {
        return searchStrategy;
    }

    @Override
    public int count() {
        return store.size();
    }
}