package io.github.songrongzhen.easyagent.rag.store;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

public class VectorStoreProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreProviderFactory.class);

    public static VectorStoreProvider create(EasyAgentRagProperties properties, VectorStore springAiVectorStore) {
        EasyAgentRagProperties.StorageType storageType = properties.getStorageType();

        if (storageType == EasyAgentRagProperties.StorageType.AUTO) {
            return createAuto(properties, springAiVectorStore);
        }

        return switch (storageType) {
            case PGVECTOR -> createPgVector(springAiVectorStore);
            case PDF, IN_MEMORY -> createInMemory();
            default -> createInMemory();
        };
    }

    private static VectorStoreProvider createAuto(EasyAgentRagProperties properties, VectorStore springAiVectorStore) {
        if (springAiVectorStore != null && properties.getPgVector().isEnabled()) {
            try {
                PgVectorStoreProvider pgProvider = new PgVectorStoreProvider(springAiVectorStore);
                if (pgProvider.isAvailable()) {
                    log.info("Using PGVector as vector store provider");
                    return pgProvider;
                }
            } catch (Exception e) {
                log.warn("PGVector not available, falling back to in-memory store: {}", e.getMessage());
            }
        }

        log.info("Using in-memory vector store provider");
        return createInMemory();
    }

    private static VectorStoreProvider createPgVector(VectorStore springAiVectorStore) {
        if (springAiVectorStore == null) {
            throw new IllegalStateException("PGVector VectorStore bean not found. Please ensure spring-ai-pgvector-store is configured.");
        }
        return new PgVectorStoreProvider(springAiVectorStore);
    }

    private static VectorStoreProvider createInMemory() {
        return new InMemoryVectorStoreProvider();
    }
}
