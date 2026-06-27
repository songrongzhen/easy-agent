package io.github.songrongzhen.easyagent.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "easy-agent.mcp")
public class EasyAgentMcpProperties {

    private boolean enabled = true;

    private String serverName = "easy-agent-mcp-server";

    private String serverVersion = "0.1.6";

    private Cors cors = new Cors();

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

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public String getServerVersion() { return serverVersion; }
    public void setServerVersion(String serverVersion) { this.serverVersion = serverVersion; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
}
