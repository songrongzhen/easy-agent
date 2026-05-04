package io.github.songrongzhen.easyagent.skill.service;

import io.github.songrongzhen.easyagent.skill.config.EasyAgentSkillProperties;
import io.github.songrongzhen.easyagent.skill.model.SkillDefinition;
import io.github.songrongzhen.easyagent.skill.parser.SkillMdParser;
import io.github.songrongzhen.easyagent.skill.registry.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SkillLoaderService {

    private static final Logger log = LoggerFactory.getLogger(SkillLoaderService.class);

    private final SkillRegistry skillRegistry;
    private final EasyAgentSkillProperties properties;

    public SkillLoaderService(SkillRegistry skillRegistry, EasyAgentSkillProperties properties) {
        this.skillRegistry = skillRegistry;
        this.properties = properties;
    }

    public void loadSkills() {
        if (!properties.isEnabled()) {
            log.info("Skill system is disabled");
            return;
        }

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String searchPattern = properties.getSkillPath() + "**/" + properties.getFilePattern();
            Resource[] resources = resolver.getResources(searchPattern);

            if (resources.length == 0) {
                log.info("No SKILL.md files found at path: {}", properties.getSkillPath());
                return;
            }

            for (Resource resource : resources) {
                try {
                    String content = resource.getContentAsString(StandardCharsets.UTF_8);
                    String sourcePath = resource.getURI().toString();
                    SkillDefinition skill = SkillMdParser.parse(content, sourcePath);
                    skillRegistry.register(skill);
                } catch (Exception e) {
                    log.error("Failed to load skill from: {}", resource.getFilename(), e);
                }
            }

            log.info("Loaded {} skills", skillRegistry.size());
        } catch (IOException e) {
            log.error("Failed to resolve skill resources", e);
        }
    }

    public void reloadSkill(String skillName) {
        SkillDefinition existing = skillRegistry.getSkill(skillName);
        if (existing != null && existing.sourcePath() != null) {
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource resource = resolver.getResource(existing.sourcePath());
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                SkillDefinition reloaded = SkillMdParser.parse(content, existing.sourcePath());
                skillRegistry.register(reloaded);
                log.info("Hot-reloaded skill: {}", skillName);
            } catch (Exception e) {
                log.error("Failed to hot-reload skill: {}", skillName, e);
            }
        }
    }

    public void reloadAllSkills() {
        skillRegistry.clear();
        loadSkills();
    }
}
