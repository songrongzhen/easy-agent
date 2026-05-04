package io.github.songrongzhen.easyagent.skill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easy-agent.skill")
public class EasyAgentSkillProperties {

    private boolean enabled = true;

    private String skillPath = "classpath:skills/";

    private boolean hotReload = true;

    private long watchInterval = 5000;

    private String filePattern = "SKILL.md";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSkillPath() { return skillPath; }
    public void setSkillPath(String skillPath) { this.skillPath = skillPath; }
    public boolean isHotReload() { return hotReload; }
    public void setHotReload(boolean hotReload) { this.hotReload = hotReload; }
    public long getWatchInterval() { return watchInterval; }
    public void setWatchInterval(long watchInterval) { this.watchInterval = watchInterval; }
    public String getFilePattern() { return filePattern; }
    public void setFilePattern(String filePattern) { this.filePattern = filePattern; }
}
