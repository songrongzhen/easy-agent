package io.github.songrongzhen.easyagent.skill.model;

import java.util.List;
import java.util.Map;

public record SkillDefinition(
        String name,
        String version,
        String description,
        String author,
        List<String> tags,
        List<String> requiredTools,
        List<SkillStep> steps,
        Map<String, Object> metadata,
        String rawContent,
        String sourcePath
) {}
