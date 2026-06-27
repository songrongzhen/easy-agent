package io.github.songrongzhen.easyagent.rag.store;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PgVector存储
 */
public class PgVectorStoreProvider implements VectorStoreProvider {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreProvider.class);

    private final String tableName;

    public PgVectorStoreProvider(EasyAgentRagProperties properties) {
        this.tableName = properties != null ? properties.getPgVector().getTableName() : "easy_agent_vector_store";
        log.warn("PgVectorStoreProvider created without dependencies. Please configure JdbcTemplate and EmbeddingModel for full functionality.");
    }

    @Override
    public void add(java.util.List<DocumentChunk> chunks) {
        log.warn("PgVector add is not available. Please configure JdbcTemplate and EmbeddingModel.");
    }

    @Override
    public java.util.List<DocumentChunk> search(String query, int topK) {
        log.warn("PgVector search is not available. Please configure JdbcTemplate and EmbeddingModel.");
        return java.util.List.of();
    }

    @Override
    public void delete(java.util.List<String> ids) {
        log.warn("PgVector delete is not available. Please configure JdbcTemplate and EmbeddingModel.");
    }

    /**
     * 删除指定来源的文档块。
     */
    @Override
    public void deleteBySource(String source) {
        log.warn("PgVector deleteBySource is not available. Please configure JdbcTemplate and EmbeddingModel.");
    }

    /**
     * 删除指定文档ID的文档块。
     */
    @Override
    public void deleteByDocumentId(String documentId) {
        log.warn("PgVector deleteByDocumentId is not available. Please configure JdbcTemplate and EmbeddingModel.");
    }

    @Override
    public void deleteAll() {
        log.warn("PgVector deleteAll is not available. Please configure JdbcTemplate and EmbeddingModel.");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getName() {
        return "pgvector-unavailable";
    }

    @Override
    public int count() {
        return 0;
    }

    @Override
    public java.util.List<DocumentChunk> getAll() {
        return java.util.List.of();
    }
}
