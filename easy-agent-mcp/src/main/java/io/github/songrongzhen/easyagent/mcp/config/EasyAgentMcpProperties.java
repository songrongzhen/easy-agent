package io.github.songrongzhen.easyagent.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "easy-agent.mcp")
public class EasyAgentMcpProperties {

    private boolean enabled = true;

    private String serverName = "easy-agent-mcp-server";

    private String serverVersion = "0.1.8";

    private Cors cors = new Cors();

    private ToolExposure toolExposure = new ToolExposure();

    public static class Cors {
        private boolean enabled = true;
        private List<String> allowedOriginPatterns = new ArrayList<>(
                List.of("http://localhost:*", "http://127.0.0.1:*")
        );
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "OPTIONS"));
        private List<String> exposedHeaders = new ArrayList<>(List.of("Content-Type"));
        private Boolean allowCredentials;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getAllowedOriginPatterns() { return allowedOriginPatterns; }
        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) { this.allowedOriginPatterns = allowedOriginPatterns; }
        public List<String> getAllowedHeaders() { return allowedHeaders; }
        public void setAllowedHeaders(List<String> allowedHeaders) { this.allowedHeaders = allowedHeaders; }
        public List<String> getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }
        public List<String> getExposedHeaders() { return exposedHeaders; }
        public void setExposedHeaders(List<String> exposedHeaders) { this.exposedHeaders = exposedHeaders; }
        public Boolean getAllowCredentials() { return allowCredentials; }
        public void setAllowCredentials(Boolean allowCredentials) { this.allowCredentials = allowCredentials; }
    }

    public static class ToolExposure {
        private boolean enabled = false;
        private List<String> allowedTools = new ArrayList<>();
        private List<String> blockedTools = new ArrayList<>();
        private List<String> allowedSources = new ArrayList<>();
        private List<String> blockedSources = new ArrayList<>();
        private List<String> allowedCategories = new ArrayList<>();
        private List<String> blockedCategories = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getAllowedTools() { return allowedTools; }
        public void setAllowedTools(List<String> allowedTools) { this.allowedTools = allowedTools; }
        public List<String> getBlockedTools() { return blockedTools; }
        public void setBlockedTools(List<String> blockedTools) { this.blockedTools = blockedTools; }
        public List<String> getAllowedSources() { return allowedSources; }
        public void setAllowedSources(List<String> allowedSources) { this.allowedSources = allowedSources; }
        public List<String> getBlockedSources() { return blockedSources; }
        public void setBlockedSources(List<String> blockedSources) { this.blockedSources = blockedSources; }
        public List<String> getAllowedCategories() { return allowedCategories; }
        public void setAllowedCategories(List<String> allowedCategories) { this.allowedCategories = allowedCategories; }
        public List<String> getBlockedCategories() { return blockedCategories; }
        public void setBlockedCategories(List<String> blockedCategories) { this.blockedCategories = blockedCategories; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public String getServerVersion() { return serverVersion; }
    public void setServerVersion(String serverVersion) { this.serverVersion = serverVersion; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public ToolExposure getToolExposure() { return toolExposure; }
    public void setToolExposure(ToolExposure toolExposure) { this.toolExposure = toolExposure; }
}
