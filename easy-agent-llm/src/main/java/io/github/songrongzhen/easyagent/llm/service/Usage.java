package io.github.songrongzhen.easyagent.llm.service;

public record Usage(
        long promptTokens,
        long completionTokens,
        long totalTokens
) {}
