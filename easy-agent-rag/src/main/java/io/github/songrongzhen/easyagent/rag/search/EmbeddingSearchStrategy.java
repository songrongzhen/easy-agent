package io.github.songrongzhen.easyagent.rag.search;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 使用大模型的Embedding服务将文本转为向量，计算余弦相似度
 */
public class EmbeddingSearchStrategy implements SearchStrategy {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSearchStrategy.class);
    
    private final EmbeddingModel embeddingModel;
    private final double minSimilarity;

    public EmbeddingSearchStrategy(EmbeddingModel embeddingModel, EasyAgentRagProperties.Embedding embeddingConfig) {
        this.embeddingModel = embeddingModel;
        this.minSimilarity = embeddingConfig != null ? embeddingConfig.getMinSimilarity() : 0.3;
    }

    @Override
    public List<DocumentChunk> prepareDocuments(List<DocumentChunk> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> prepared = new ArrayList<>(documents.size());
        for (DocumentChunk doc : documents) {
            if (doc.embedding() != null) {
                prepared.add(doc);
                continue;
            }

            try {
                prepared.add(doc.withEmbedding(embeddingModel.embed(doc.content())));
            } catch (Exception e) {
                log.warn("Failed to create embedding for document: source={}, id={}, message={}",
                        doc.source(), doc.id(), e.getMessage());
                prepared.add(doc);
            }
        }
        return prepared;
    }

    @Override
    public List<DocumentChunk> search(String query, List<DocumentChunk> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        
        try {
            // 获取查询向量
            float[] queryEmbedding = embeddingModel.embed(query);
            List<ScoredChunk> scored = new ArrayList<>();
            
            for (DocumentChunk doc : documents) {
                float[] docEmbedding = doc.embedding();
                if (docEmbedding == null) {
                    docEmbedding = embeddingModel.embed(doc.content());
                }
                
                double similarity = cosineSimilarity(queryEmbedding, docEmbedding);
                if (similarity >= minSimilarity) {
                    scored.add(new ScoredChunk(doc, similarity));
                }
            }
            
            // 按相似度排序
            scored.sort((a, b) -> Double.compare(b.similarity, a.similarity));
            
            log.debug("Embedding search: query={}, topK={}, matched={}", query, topK, scored.size());
            
            return scored.stream()
                    .limit(topK)
                    .map(scoredChunk -> scoredChunk.chunk().withScore(scoredChunk.similarity()))
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Embedding search failed, returning empty result: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public String getName() {
        return "EMBEDDING";
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            return 0;
        }
        
        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        double denominator = Math.sqrt(norm1) * Math.sqrt(norm2);
        return denominator == 0 ? 0 : dotProduct / denominator;
    }

    private record ScoredChunk(DocumentChunk chunk, double similarity) {}
}
