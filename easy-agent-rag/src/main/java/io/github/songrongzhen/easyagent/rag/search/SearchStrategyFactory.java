package io.github.songrongzhen.easyagent.rag.search;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties.SearchStrategyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索策略工厂
 * 根据配置自动选择合适的搜索策略
 */
public class SearchStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(SearchStrategyFactory.class);

    /**
     * 创建搜索策略
     *
     * @param properties     RAG配置
     * @param embeddingModel Embedding模型（可选）
     * @return 搜索策略实例
     */
    public static SearchStrategy create(EasyAgentRagProperties properties, EmbeddingModel embeddingModel) {
        SearchStrategyType configuredStrategy = properties.getSearch().getStrategy();
        
        // 用户明确指定策略
        if (configuredStrategy != SearchStrategyType.AUTO) {
            log.info("Using explicitly configured strategy: {}", configuredStrategy);
            return createStrategy(configuredStrategy, properties, embeddingModel);
        }
        
        // 自动策略使用降级链
        return createWithFallback(properties, embeddingModel);
    }

    /**
     * 自动选择策略
     */
    private static SearchStrategy createAutoStrategy(EasyAgentRagProperties properties, EmbeddingModel embeddingModel) {
        return createWithFallback(properties, embeddingModel);
    }

    /**
     * 创建指定策略
     */
    private static SearchStrategy createStrategy(SearchStrategyType strategy, 
                                                   EasyAgentRagProperties properties,
                                                   EmbeddingModel embeddingModel) {
        return switch (strategy) {
            case EMBEDDING -> {
                if (embeddingModel == null) {
                    log.warn("Embedding model not available, falling back to COSINE");
                    yield createStrategy(SearchStrategyType.COSINE, properties, embeddingModel);
                }
                yield new EmbeddingSearchStrategy(embeddingModel, properties.getSearch().getEmbedding());
            }
            case COSINE -> new CosineSimilaritySearchStrategy(properties.getSearch().getCosine());
            case TF_IDF -> new TfIdfSearchStrategy(properties.getSearch().getTfIdf());
            case AUTO -> createAutoStrategy(properties, embeddingModel);
        };
    }

    /**
     * 检测Embedding服务是否可用
     */
    private static boolean isEmbeddingAvailable(EasyAgentRagProperties properties, EmbeddingModel embeddingModel) {
        // 检查配置
        if (!properties.getSearch().getEmbedding().isEnabled()) {
            log.debug("Embedding is disabled in configuration");
            return false;
        }
        
        // 检查模型
        if (embeddingModel == null) {
            log.debug("Embedding model is null");
            return false;
        }
        return true;
    }

    /**
     * 创建带降级的策略链
     */
    public static SearchStrategy createWithFallback(EasyAgentRagProperties properties, EmbeddingModel embeddingModel) {
        List<SearchStrategy> strategies = new ArrayList<>();
        
        // 按优先级添加策略
        if (isEmbeddingAvailable(properties, embeddingModel)) {
            strategies.add(new EmbeddingSearchStrategy(embeddingModel, properties.getSearch().getEmbedding()));
        }
        
        if (properties.getSearch().getCosine().isEnabled()) {
            strategies.add(new CosineSimilaritySearchStrategy(properties.getSearch().getCosine()));
        }
        
        if (properties.getSearch().getTfIdf().isEnabled()) {
            strategies.add(new TfIdfSearchStrategy(properties.getSearch().getTfIdf()));
        }
        if (strategies.isEmpty()) {
            return new EmptySearchStrategy();
        }
        
        return new FallbackSearchStrategy(strategies);
    }

    /**
     * 降级搜索策略
     */
    private static class FallbackSearchStrategy implements SearchStrategy {
        
        private final List<SearchStrategy> strategies;

        public FallbackSearchStrategy(List<SearchStrategy> strategies) {
            this.strategies = strategies;
        }

        @Override
        public List<io.github.songrongzhen.easyagent.rag.store.DocumentChunk> prepareDocuments(
                List<io.github.songrongzhen.easyagent.rag.store.DocumentChunk> documents) {
            List<io.github.songrongzhen.easyagent.rag.store.DocumentChunk> prepared = documents;
            for (SearchStrategy strategy : strategies) {
                try {
                    prepared = strategy.prepareDocuments(prepared);
                } catch (Exception e) {
                    log.warn("Strategy {} failed to prepare documents: {}", strategy.getName(), e.getMessage());
                }
            }
            return prepared;
        }

        @Override
        public List<io.github.songrongzhen.easyagent.rag.store.DocumentChunk> search(
                String query, 
                List<io.github.songrongzhen.easyagent.rag.store.DocumentChunk> documents, 
                int topK) {
            
            for (SearchStrategy strategy : strategies) {
                try {
                    var results = strategy.search(query, documents, topK);
                    if (!results.isEmpty()) {
                        log.info("Search succeeded with strategy: {}", strategy.getName());
                        return results;
                    }
                } catch (Exception e) {
                    log.warn("Strategy {} failed, trying next: {}", strategy.getName(), e.getMessage());
                }
            }
            return List.of();
        }

        @Override
        public String getName() {
            return "FALLBACK";
        }
    }

    private static class EmptySearchStrategy implements SearchStrategy {

        @Override
        public List<io.github.songrongzhen.easyagent.rag.store.DocumentChunk> search(
                String query,
                List<io.github.songrongzhen.easyagent.rag.store.DocumentChunk> documents,
                int topK) {
            return List.of();
        }

        @Override
        public String getName() {
            return "EMPTY";
        }
    }
}
