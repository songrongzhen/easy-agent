package io.github.songrongzhen.easyagent.skill.registry;

import io.github.songrongzhen.easyagent.skill.model.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();

    public void register(SkillDefinition skill) {
        if (skill == null || skill.name() == null || skill.name().isBlank()) {
            log.warn("Attempted to register a skill with null or blank name, ignoring");
            return;
        }
        skills.put(skill.name(), skill);
        log.info("Registered skill: {} (v{}) - {}", skill.name(), skill.version(), skill.description());
    }

    public void unregister(String skillName) {
        SkillDefinition removed = skills.remove(skillName);
        if (removed != null) {
            log.info("Unregistered skill: {}", skillName);
        }
    }

    public SkillDefinition getSkill(String skillName) {
        return skills.get(skillName);
    }

    public Collection<SkillDefinition> getAllSkills() {
        return Collections.unmodifiableCollection(skills.values());
    }

    public boolean contains(String skillName) {
        return skills.containsKey(skillName);
    }

    public int size() {
        return skills.size();
    }

    public void clear() {
        skills.clear();
        log.info("Skill registry cleared");
    }
}
