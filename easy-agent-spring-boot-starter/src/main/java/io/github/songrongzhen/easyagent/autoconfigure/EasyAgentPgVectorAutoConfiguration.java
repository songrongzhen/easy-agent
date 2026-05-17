package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.store.InMemoryVectorStoreProvider;
import io.github.songrongzhen.easyagent.rag.store.PgVectorStoreProvider;
import io.github.songrongzhen.easyagent.rag.store.VectorStoreProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(VectorStore.class)
@ConditionalOnProperty(prefix = "easy-agent.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EasyAgentPgVectorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(VectorStoreProvider.class)
    public VectorStoreProvider pgVectorStoreProvider(EasyAgentRagProperties properties,
                                                     ApplicationContext applicationContext) {
        VectorStore springAiVectorStore = null;
        try {
            springAiVectorStore = applicationContext.getBean(VectorStore.class);
        } catch (Exception ignored) {
        }

        if (springAiVectorStore != null && properties.getPgVector().isEnabled()) {
            PgVectorStoreProvider pgProvider = new PgVectorStoreProvider(springAiVectorStore);
            if (pgProvider.isAvailable()) {
                return pgProvider;
            }
        }

        return new InMemoryVectorStoreProvider();
    }
}
