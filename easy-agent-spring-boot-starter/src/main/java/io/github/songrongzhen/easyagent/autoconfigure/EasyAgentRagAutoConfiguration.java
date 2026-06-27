package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.search.SearchStrategy;
import io.github.songrongzhen.easyagent.rag.search.SearchStrategyFactory;
import io.github.songrongzhen.easyagent.rag.service.RagService;
import io.github.songrongzhen.easyagent.rag.store.InMemoryVectorStoreProvider;
import io.github.songrongzhen.easyagent.rag.store.PgVectorStoreProvider;
import io.github.songrongzhen.easyagent.rag.store.VectorStoreProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "io.github.songrongzhen.easyagent.rag.service.RagService")
@ConditionalOnProperty(prefix = "easy-agent.rag", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(EasyAgentRagProperties.class)
public class EasyAgentRagAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EasyAgentRagAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(VectorStoreProvider.class)
    public VectorStoreProvider vectorStoreProvider(EasyAgentRagProperties properties,
                                                  @Autowired(required = false) EmbeddingModel embeddingModel) {
        log.info("Creating VectorStoreProvider with storageType={}, strategy={}",
                properties.getStorageType(), properties.getSearch().getStrategy());

        if (properties.getStorageType() == EasyAgentRagProperties.StorageType.PGVECTOR) {
            return new PgVectorStoreProvider(properties);
        }

        SearchStrategy strategy = SearchStrategyFactory.create(properties, embeddingModel);
        return new InMemoryVectorStoreProvider(strategy);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagService ragService(VectorStoreProvider vectorStoreProvider, EasyAgentRagProperties properties) {
        return new RagService(vectorStoreProvider, properties);
    }
}
