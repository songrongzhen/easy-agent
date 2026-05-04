package io.github.songrongzhen.easyagent.rag.store;

import java.util.List;
import java.util.Optional;

public interface VectorStoreProvider {

    void add(List<DocumentChunk> chunks);

    List<DocumentChunk> search(String query, int topK);

    void delete(List<String> ids);

    void deleteAll();

    boolean isAvailable();

    String getName();
}
