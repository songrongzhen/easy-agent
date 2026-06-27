package io.github.songrongzhen.easyagent.llm.service;

import java.util.List;

public record ChatMessage(
        Role role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId
) {
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    public ChatMessage(Role role, String content) {
        this(role, content, List.of(), null);
    }

    public ChatMessage {
        if (toolCalls == null) {
            toolCalls = List.of();
        }
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    public static ChatMessage assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(Role.ASSISTANT, content, toolCalls, null);
    }

    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage(Role.TOOL, content, List.of(), toolCallId);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
