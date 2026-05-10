package io.github.songrongzhen.easyagent.rag.store;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VectorStoreProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreProviderFactory.class);

    public static VectorStoreProvider create(EasyAgentRagProperties properties) {
        EasyAgentRagProperties.StorageType storageType = properties.getStorageType();

        if (storageType == EasyAgentRagProperties.StorageType.AUTO) {
            log.info("Using in-memory vector store provider (auto mode, no PGVector available)");
            return createInMemory();
        }

        return switch (storageType) {
            case PGVECTOR -> {
                throw new IllegalStateException(
                        "PGVector storage type requires Spring AI and PGVector on the classpath. " +
                        "Please add spring-ai-pgvector-store dependency or change storage-type to auto/in-memory.");
            }
            case PDF, IN_MEMORY -> createInMemory();
            default -> createInMemory();
        };
    }

    public static VectorStoreProvider createInMemory() {
        return new InMemoryVectorStoreProvider();
    }
}
