package io.github.songrongzhen.easyagent.skill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easy-agent.skill")
public class EasyAgentSkillProperties {

    private boolean enabled = true;

    private String skillOutputPath = ".";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSkillOutputPath() {
        return skillOutputPath;
    }

    public void setSkillOutputPath(String skillOutputPath) {
        this.skillOutputPath = skillOutputPath;
    }
}
