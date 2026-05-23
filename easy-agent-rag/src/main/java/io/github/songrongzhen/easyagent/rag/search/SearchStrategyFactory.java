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
 * 
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
        
        // 自动选择策略
        return createAutoStrategy(properties, embeddingModel);
    }

    /**
     * 自动选择策略
     */
    private static SearchStrategy createAutoStrategy(EasyAgentRagProperties properties, EmbeddingModel embeddingModel) {
        // 优先级1: Embedding策略
        if (isEmbeddingAvailable(properties, embeddingModel)) {
            log.info("Auto-selecting EMBEDDING search strategy (most accurate)");
            return createStrategy(SearchStrategyType.EMBEDDING, properties, embeddingModel);
        }
        
        // 优先级2: 余弦相似度策略
        if (properties.getSearch().getCosine().isEnabled()) {
            log.info("Auto-selecting COSINE search strategy (fallback)");
            return createStrategy(SearchStrategyType.COSINE, properties, embeddingModel);
        }
        
        // 优先级3: TF-IDF策略（兜底）
        log.info("Auto-selecting TF-IDF search strategy (last fallback)");
        return createStrategy(SearchStrategyType.TF_IDF, properties, embeddingModel);
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
        
        // 测试服务可用性
        try {
            embeddingModel.embed("test");
            log.debug("Embedding service is available");
            return true;
        } catch (Exception e) {
            log.debug("Embedding service test failed: {}", e.getMessage());
            return false;
        }
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
        
        strategies.add(new TfIdfSearchStrategy(properties.getSearch().getTfIdf()));
        
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
        public List<io.github.songrongzhen.easyagent.rag.store.DocumentChunk> search(
                String query, 
                List<io.github.songrongzhen.easyagent.rag.store.DocumentChunk> documents, 
                int topK) {
            
            for (SearchStrategy strategy : strategies) {
                try {
                    log.debug("Trying search strategy: {}", strategy.getName());
                    var results = strategy.search(query, documents, topK);
                    if (!results.isEmpty()) {
                        log.info("Search succeeded with strategy: {}", strategy.getName());
                        return results;
                    }
                } catch (Exception e) {
                    log.warn("Strategy {} failed, trying next: {}", strategy.getName(), e.getMessage());
                }
            }
            
            log.warn("All search strategies failed, returning empty result");
            return List.of();
        }
    }
}
