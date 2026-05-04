package io.github.songrongzhen.easyagent.llm.service;

import java.util.List;

public record ToolDescriptor(
        String name,
        String description,
        List<ToolParameter> parameters
) {}
