package io.github.songrongzhen.easyagent.core.model;

public record ParameterDefinition(
        String name,
        String description,
        String type,
        boolean required
) {}
