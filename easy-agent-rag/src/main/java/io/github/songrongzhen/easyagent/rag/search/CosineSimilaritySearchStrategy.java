package io.github.songrongzhen.easyagent.rag.search;

import io.github.songrongzhen.easyagent.rag.config.EasyAgentRagProperties;
import io.github.songrongzhen.easyagent.rag.store.DocumentChunk;

import java.util.*;
import java.util.stream.Collectors;

public class CosineSimilaritySearchStrategy implements SearchStrategy {
    
    private final double minSimilarity;

    public CosineSimilaritySearchStrategy(EasyAgentRagProperties.Cosine cosineConfig) {
        this.minSimilarity = cosineConfig != null ? cosineConfig.getMinSimilarity() : 0.1;
    }

    public CosineSimilaritySearchStrategy() {
        this.minSimilarity = 0.1;
    }

    @Override
    public List<DocumentChunk> search(String query, List<DocumentChunk> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        
        Set<String> vocabulary = new HashSet<>();
        Set<String> queryWords = tokenize(query);        
        vocabulary.addAll(queryWords);
        
        List<Set<String>> docWordsList = new ArrayList<>();
        for (DocumentChunk doc : documents) {
            Set<String> docWords = tokenize(doc.content());
            docWordsList.add(docWords);
            vocabulary.addAll(docWords);
        }
        
        if (vocabulary.isEmpty()) {
            return documents.stream().limit(topK).collect(Collectors.toList());
        }
        
        List<String> vocabList = new ArrayList<>(vocabulary);
        Map<String, Integer> vocabIndex = new HashMap<>();
        for (int i = 0; i < vocabList.size(); i++) {
            vocabIndex.put(vocabList.get(i), i);
        }
        
        double[] queryVector = buildVector(queryWords, vocabList, vocabIndex);
        List<double[]> docVectors = new ArrayList<>();
        for (Set<String> docWords : docWordsList) {
            docVectors.add(buildVector(docWords, vocabList, vocabIndex));
        }
        
        List<ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            double similarity = cosineSimilarity(queryVector, docVectors.get(i));
            if (similarity >= minSimilarity) {
                scored.add(new ScoredChunk(documents.get(i), similarity));
            }
        }
        
        scored.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        
        return scored.stream()
                .limit(topK)
                .map(scoredChunk -> scoredChunk.chunk().withScore(scoredChunk.similarity()))
                .collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return "COSINE";
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        
        Set<String> words = new HashSet<>();
        String[] parts = text.toLowerCase().split("[\\s，。、！？；：\"\"''【】（）,.:;!?\"'()\\[\\]{}]+");
        for (String part : parts) {
            if (part.length() >= 2) {
                words.add(part);
            }
            for (int i = 0; i < part.length() - 1; i++) {
                words.add(part.substring(i, i + 2));
            }
        }
        return words;
    }

    private double[] buildVector(Set<String> words, List<String> vocabList, Map<String, Integer> vocabIndex) {
        double[] vector = new double[vocabList.size()];
        for (String word : words) {
            Integer index = vocabIndex.get(word);
            if (index != null) {
                vector[index] = 1.0;
            }
        }
        return vector;
    }

    private double cosineSimilarity(double[] vec1, double[] vec2) {
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
