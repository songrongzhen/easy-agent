package io.github.songrongzhen.easyagent.core.model;

import java.util.List;

public record ToolDefinition(
        String name,
        String description,
        String category,
        String beanName,
        String methodName,
        List<ParameterDefinition> parameters,
        boolean enabled
) {}
