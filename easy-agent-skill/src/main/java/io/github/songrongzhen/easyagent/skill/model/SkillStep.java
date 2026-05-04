package io.github.songrongzhen.easyagent.skill.model;

public record SkillStep(
        int order,
        String name,
        String description,
        String action,
        String tool,
        String inputTemplate,
        String outputTemplate,
        String condition
) {}
