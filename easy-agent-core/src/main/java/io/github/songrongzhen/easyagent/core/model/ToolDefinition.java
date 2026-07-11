package io.github.songrongzhen.easyagent.core.model;

import java.util.List;

public record ToolDefinition(
        String name,
        String description,
        String category,
        String source,
        int priority,
        String beanName,
        String methodName,
        List<ParameterDefinition> parameters,
        String version,
        boolean enabled
) {}
