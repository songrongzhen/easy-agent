package io.github.songrongzhen.easyagent.llm.service;

import java.util.List;

public record ChatMessage(
        Role role,
        String content
) {
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
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
}
