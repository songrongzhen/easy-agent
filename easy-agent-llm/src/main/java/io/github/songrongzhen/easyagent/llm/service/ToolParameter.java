package io.github.songrongzhen.easyagent.llm.service;

public record ToolParameter(
        String name,
        String type,
        String description,
        boolean required
) {}
