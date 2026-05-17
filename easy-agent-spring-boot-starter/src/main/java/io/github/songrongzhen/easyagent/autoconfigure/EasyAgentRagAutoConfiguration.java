package io.github.songrongzhen.easyagent.autoconfigure;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.service.RagService;
import io.github.songrongzhen.easyagent.rag.store.VectorStoreProvider;
import io.github.songrongzhen.easyagent.rag.store.VectorStoreProviderFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;

@AutoConfiguration
@ConditionalOnClass(name = "io.github.songrongzhen.easyagent.rag.service.RagService")
@ConditionalOnProperty(prefix = "easy-agent.rag", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(EasyAgentRagProperties.class)
public class EasyAgentRagAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(VectorStoreProvider.class)
    public VectorStoreProvider vectorStoreProvider(EasyAgentRagProperties properties) {
        return VectorStoreProviderFactory.create(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RagService ragService(VectorStoreProvider vectorStoreProvider, EasyAgentRagProperties properties) {
        return new RagService(vectorStoreProvider, properties);
    }

    @Bean
    public RagIndexInitializer ragIndexInitializer(RagService ragService) {
        return new RagIndexInitializer(ragService);
    }

    public static class RagIndexInitializer implements ApplicationListener<ContextRefreshedEvent> {
        private final RagService ragService;
        private volatile boolean initialized = false;

        public RagIndexInitializer(RagService ragService) {
            this.ragService = ragService;
        }

        @Override
        public void onApplicationEvent(ContextRefreshedEvent event) {
            if (!initialized) {
                initialized = true;
                ragService.indexAllDocuments();
            }
        }
    }
}
