package io.github.songrongzhen.easyagent.rag.store;

import java.util.Map;

public record DocumentChunk(
        String id,
        String content,
        String source,
        Map<String, Object> metadata,
        float[] embedding,
        Double score
) {
    public DocumentChunk(String id, String content, String source, Map<String, Object> metadata, float[] embedding) {
        this(id, content, source, metadata, embedding, null);
    }

    /**
     * 返回带文档向量的文档块。
     */
    public DocumentChunk withEmbedding(float[] embedding) {
        return new DocumentChunk(id, content, source, metadata, embedding, score);
    }

    /**
     * 返回带相关性分数的文档块。
     */
    public DocumentChunk withScore(double score) {
        return new DocumentChunk(id, content, source, metadata, embedding, score);
    }

    /**
     * 返回不带相关性分数的文档块。
     */
    public DocumentChunk withoutScore() {
        return new DocumentChunk(id, content, source, metadata, embedding, null);
    }
}
