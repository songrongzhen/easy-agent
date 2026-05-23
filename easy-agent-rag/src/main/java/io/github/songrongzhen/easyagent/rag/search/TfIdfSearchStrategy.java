package io.github.songrongzhen.easyagent.rag.search;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于TF-IDF的关键词搜索策略
 * 
 * 无需外部服务，使用词频统计进行搜索
 */
public class TfIdfSearchStrategy implements SearchStrategy {

    private static final Logger log = LoggerFactory.getLogger(TfIdfSearchStrategy.class);
    
    private final double minScore;

    public TfIdfSearchStrategy(EasyAgentRagProperties.TfIdf tfIdfConfig) {
        this.minScore = tfIdfConfig != null ? tfIdfConfig.getMinScore() : 0.1;
    }

    public TfIdfSearchStrategy() {
        this.minScore = 0.1;
    }

    @Override
    public List<DocumentChunk> search(String query, List<DocumentChunk> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        
        // 分词
        Set<String> queryWords = tokenize(query);
        if (queryWords.isEmpty()) {
            return documents.stream().limit(topK).collect(Collectors.toList());
        }
        
        // 计算IDF
        Map<String, Double> idf = calculateIDF(documents, queryWords);
        
        // 计算每个文档的TF-IDF得分
        List<ScoredChunk> scored = new ArrayList<>();
        for (DocumentChunk doc : documents) {
            Set<String> docWords = tokenize(doc.content());
            double score = calculateTfIdfScore(queryWords, docWords, idf);
            if (score >= minScore) {
                scored.add(new ScoredChunk(doc, score));
            }
        }
        
        // 按得分排序
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        
        log.debug("TF-IDF search: query={}, topK={}, matched={}", query, topK, scored.size());
        
        return scored.stream()
                .limit(topK)
                .map(ScoredChunk::chunk)
                .collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return "TF-IDF";
    }

    /**
     * 简单中文分词（基于字符）
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        
        Set<String> words = new HashSet<>();
        // 简单分词：按空格、标点分割
        String[] parts = text.toLowerCase().split("[\\s，。、！？；：\"\"''【】（）,.:;!?\"'()\\[\\]{}]+");
        for (String part : parts) {
            if (part.length() >= 2) {
                words.add(part);
            }
            // 中文bigram
            for (int i = 0; i < part.length() - 1; i++) {
                words.add(part.substring(i, i + 2));
            }
        }
        return words;
    }

    /**
     * 计算IDF（逆文档频率）
     */
    private Map<String, Double> calculateIDF(List<DocumentChunk> documents, Set<String> queryWords) {
        Map<String, Double> idf = new HashMap<>();
        int totalDocs = documents.size();
        
        for (String word : queryWords) {
            int docCount = 0;
            for (DocumentChunk doc : documents) {
                if (tokenize(doc.content()).contains(word)) {
                    docCount++;
                }
            }
            // IDF = log(总文档数 / 包含该词的文档数)
            idf.put(word, docCount > 0 ? Math.log((double) totalDocs / docCount) : 0);
        }
        
        return idf;
    }

    /**
     * 计算TF-IDF得分
     */
    private double calculateTfIdfScore(Set<String> queryWords, Set<String> docWords, Map<String, Double> idf) {
        double score = 0;
        for (String word : queryWords) {
            if (docWords.contains(word)) {
                double idfValue = idf.getOrDefault(word, 0.0);
                // 简单的TF计算
                double tf = 1.0;
                score += tf * idfValue;
            }
        }
        return score;
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {}
}
