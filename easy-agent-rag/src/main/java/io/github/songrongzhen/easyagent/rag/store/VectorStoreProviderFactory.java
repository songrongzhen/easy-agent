package io.github.songrongzhen.easyagent.rag.store;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 向量存储提供者工厂
 */
public class VectorStoreProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreProviderFactory.class);

    public static VectorStoreProvider create(EasyAgentRagProperties properties) {
        StorageType storageType = properties.getStorageType();
        
        if (storageType == StorageType.AUTO) {
            return createAuto(properties);
        }
        
        return switch (storageType) {
            case PGVECTOR -> {
                yield createInMemory(properties);
            }
            case IN_MEMORY -> createInMemory(properties);
            default -> {
                yield createInMemory(properties);
            }
        };
    }

    private static VectorStoreProvider createAuto(EasyAgentRagProperties properties) {
        // 使用内存存储
        return createInMemory(properties);
    }

    private static VectorStoreProvider createInMemory(EasyAgentRagProperties properties) {
        return new InMemoryVectorStoreProvider(properties);
    }
}
