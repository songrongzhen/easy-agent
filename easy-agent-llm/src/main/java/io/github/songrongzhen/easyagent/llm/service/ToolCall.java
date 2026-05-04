package io.github.songrongzhen.easyagent.llm.service;

public record ToolCall(
        String id,
        String name,
        String arguments
) {}
