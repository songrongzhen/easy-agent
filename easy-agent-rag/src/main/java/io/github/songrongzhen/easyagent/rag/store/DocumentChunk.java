package io.github.songrongzhen.easyagent.rag.store;

import java.util.Map;

public record DocumentChunk(
        String id,
        String content,
        String source,
        Map<String, Object> metadata,
        float[] embedding
) {}
