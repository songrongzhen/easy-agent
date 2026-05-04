package io.github.songrongzhen.easyagent.llm.service;

import java.util.List;

public record ChatResponse(
        String content,
        String model,
        List<ToolCall> toolCalls,
        Usage usage
) {
    public static ChatResponse of(String content, String model) {
        return new ChatResponse(content, model, List.of(), null);
    }

    public static ChatResponse ofToolCalls(List<ToolCall> toolCalls, String model) {
        return new ChatResponse(null, model, toolCalls, null);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
